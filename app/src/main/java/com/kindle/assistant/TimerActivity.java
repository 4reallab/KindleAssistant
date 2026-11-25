package com.kindle.assistant;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 待办计时：快捷时长/滑条设置，开始后显示线性变化的沙漏，结束时把耗时累加到待办。 */
public class TimerActivity extends Activity {

    private static final String TIMER_PREFS = "timer_state";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_ID = "id";
    private static final String KEY_TOTAL = "total";
    private static final String KEY_REMAINING = "remaining";

    private TextView titleText;
    private TextView selectedText;
    private TextView clockText;
    private TextView resultText;
    private View setupSection;
    private View timerSpacer;
    private HourglassView hourglass;
    private SeekBar durationBar;
    private Button startButton;
    private Button pauseButton;
    private Button endButton;

    private final Handler handler = new Handler();
    private final SimpleDateFormat clockFormat =
            new SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.CHINA);

    /** 对齐整分钟刷新，避免显示比系统状态栏滞后近 1 分钟。 */
    private static long millisToNextMinute() {
        return 60_000L - (System.currentTimeMillis() % 60_000L);
    }

    private long todoId = -1;
    private int minutes = 10;
    private long totalMillis;
    private long remainingMillis;
    private long deadline;
    private boolean started;
    private boolean running;
    private boolean finished;
    private boolean clearStateOnExit;

    /** 界面（时钟与沙漏）对齐整分钟刷新，不再显示秒。 */
    private final Runnable displayTicker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            updateHourglass();
            handler.postDelayed(this, millisToNextMinute());
        }
    };

    /** 倒计时到点由独立定时器精确触发，避免受分钟刷新间隔影响。 */
    private final Runnable finishRunnable = new Runnable() {
        @Override
        public void run() {
            finishTimer();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timer);

        titleText = findViewById(R.id.timerTitle);
        selectedText = findViewById(R.id.selectedDuration);
        clockText = findViewById(R.id.clockText);
        resultText = findViewById(R.id.resultText);
        setupSection = findViewById(R.id.setupSection);
        timerSpacer = findViewById(R.id.timerSpacer);
        hourglass = findViewById(R.id.hourglass);
        durationBar = findViewById(R.id.durationBar);
        startButton = findViewById(R.id.startButton);
        pauseButton = findViewById(R.id.pauseButton);
        endButton = findViewById(R.id.endButton);

        todoId = getIntent().getLongExtra("id", -1);
        String todoTitle = getIntent().getStringExtra("title");
        titleText.setText(todoTitle == null ? "待办计时" : todoTitle);

        durationBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                minutes = progress + 1;
                selectedText.setText(getString(R.string.timer_selected, minutes));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        durationBar.setProgress(9);
        selectedText.setText(getString(R.string.timer_selected, minutes));

        restoreSavedState();

        setQuickTime(R.id.min5, 5);
        setQuickTime(R.id.min10, 10);
        setQuickTime(R.id.min15, 15);
        setQuickTime(R.id.min30, 30);
        setQuickTime(R.id.min60, 60);

        startButton.setOnClickListener(v -> startOrResume());
        pauseButton.setOnClickListener(v -> {
            if (!running || finished) {
                return;
            }
            remainingMillis = Math.max(0, deadline - SystemClock.elapsedRealtime());
            running = false;
            handler.removeCallbacks(finishRunnable);
            updateButtons();
        });
        endButton.setOnClickListener(v -> {
            if (finished) {
                finish();
            } else {
                endTimer();
            }
        });

        updateButtons();
        updateClock();
        handler.post(displayTicker);
    }

    /** 再次进入时恢复上次退出时的暂停状态，不自动继续倒计时；只恢复同一条待办。 */
    private void restoreSavedState() {
        SharedPreferences prefs = getSharedPreferences(TIMER_PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ACTIVE, false)) {
            return;
        }
        long savedId = prefs.getLong(KEY_ID, -1);
        long total = prefs.getLong(KEY_TOTAL, 0);
        long remaining = prefs.getLong(KEY_REMAINING, 0);
        if (savedId != todoId || total <= 0 || remaining < 0 || remaining > total) {
            clearSavedState();
            return;
        }
        started = true;
        running = false;
        totalMillis = total;
        remainingMillis = remaining;
        setupSection.setVisibility(View.GONE);
        timerSpacer.setVisibility(View.GONE);
        hourglass.setVisibility(View.VISIBLE);
    }

    /** 离开页面即暂停并把剩余时长写入本地，下次进入可继续。 */
    private void savePausedState() {
        if (!started || finished || clearStateOnExit) {
            return;
        }
        if (running) {
            remainingMillis = Math.max(0, deadline - SystemClock.elapsedRealtime());
            running = false;
            handler.removeCallbacks(finishRunnable);
            updateButtons();
        }
        if (remainingMillis <= 0) {
            finishTimer();
            return;
        }
        getSharedPreferences(TIMER_PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putLong(KEY_ID, todoId)
                .putLong(KEY_TOTAL, totalMillis)
                .putLong(KEY_REMAINING, remainingMillis)
                .apply();
    }

    private void clearSavedState() {
        getSharedPreferences(TIMER_PREFS, MODE_PRIVATE).edit().clear().apply();
    }

    private void endTimer() {
        if (!started) {
            clearStateOnExit = true;
            clearSavedState();
            finish();
            return;
        }
        finishTimer();
    }

    /** 归零或手动结束：记录本次耗时，展示结果，等待用户返回待办。 */
    private void finishTimer() {
        if (finished) {
            return;
        }
        handler.removeCallbacks(finishRunnable);
        if (running) {
            remainingMillis = Math.max(0, deadline - SystemClock.elapsedRealtime());
        }
        long elapsed = Math.max(0, totalMillis - remainingMillis);
        running = false;
        finished = true;
        clearStateOnExit = true;
        clearSavedState();

        TodoStore.Todo todo = todoId > 0 ? TodoStore.get(todoId) : null;
        long previous = todo == null ? 0 : todo.elapsedMillis;
        if (todoId > 0) {
            TodoStore.addElapsed(this, todoId, elapsed);
        }
        resultText.setText(getString(R.string.timer_result,
                TodoStore.durationText(elapsed),
                TodoStore.durationText(previous + elapsed)));
        resultText.setVisibility(View.VISIBLE);
        long elapsedMin = elapsed > 0 ? (elapsed + 59_999) / 60_000 : 0;
        hourglass.update(0f, 0, elapsedMin, false);
        updateButtons();
        updateClock();
    }

    @Override
    protected void onPause() {
        savePausedState();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateButtons();
        updateHourglass();
    }

    private void setQuickTime(int id, int value) {
        findViewById(id).setOnClickListener(v -> durationBar.setProgress(value - 1));
    }

    private void startOrResume() {
        if (finished) {
            return;
        }
        if (!started) {
            started = true;
            totalMillis = minutes * 60_000L;
            remainingMillis = totalMillis;
            setupSection.setVisibility(View.GONE);
            timerSpacer.setVisibility(View.GONE);
            hourglass.setVisibility(View.VISIBLE);
        }
        deadline = SystemClock.elapsedRealtime() + remainingMillis;
        running = true;
        handler.removeCallbacks(finishRunnable);
        handler.postDelayed(finishRunnable, remainingMillis);
        updateButtons();
        updateClock();
        updateHourglass();
    }

    private void updateButtons() {
        if (finished) {
            startButton.setEnabled(false);
            pauseButton.setEnabled(false);
            endButton.setEnabled(true);
            endButton.setText(R.string.timer_back);
            return;
        }
        startButton.setEnabled(!running);
        startButton.setText(started && !running ? R.string.timer_resume : R.string.timer_start);
        pauseButton.setEnabled(running);
        endButton.setEnabled(true);
        endButton.setText(R.string.timer_end);
    }

    private void updateClock() {
        clockText.setText(clockFormat.format(new Date()));
    }

    private void updateHourglass() {
        if (!started) {
            return;
        }
        // 运行中按截止时间实时计算剩余；暂停时沿用已保存的剩余值。
        if (running && !finished) {
            remainingMillis = Math.max(0, deadline - SystemClock.elapsedRealtime());
        }
        long remainingMin = (remainingMillis + 59_999) / 60_000;
        long elapsedMin = Math.max(0, (totalMillis - remainingMillis) / 60_000);
        float ratio = totalMillis == 0 ? 0f : (float) remainingMillis / totalMillis;
        hourglass.update(ratio, remainingMin, elapsedMin, running);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
