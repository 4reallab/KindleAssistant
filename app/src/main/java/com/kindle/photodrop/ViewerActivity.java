package com.kindle.photodrop;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 全屏看图：左右滑动切换，边缘滑动返回；点击图片显示旋转按钮，10 秒无操作自动隐藏。 */
public class ViewerActivity extends Activity {

    private static final long ROTATE_BUTTON_TIMEOUT = 10_000L;

    private List<String> paths = new ArrayList<>();
    private int index;
    private ImageView fullImage;
    private Button rotateButton;
    private TextView hint;
    private final Handler handler = new Handler();

    private Bitmap baseBitmap;
    private Bitmap shownBitmap;
    private int rotation;

    private float downX;
    private float downY;
    private boolean fromEdge;

    private final Runnable hideRotate = () -> {
        if (rotateButton != null) {
            rotateButton.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_viewer);

        fullImage = findViewById(R.id.fullImage);
        rotateButton = findViewById(R.id.rotateButton);
        hint = findViewById(R.id.viewerHint);
        View root = findViewById(R.id.viewerRoot);

        ArrayList<String> received = getIntent().getStringArrayListExtra("paths");
        if (received != null) {
            paths = received;
        }
        index = getIntent().getIntExtra("index", 0);
        if (paths.isEmpty()) {
            finish();
            return;
        }
        if (index < 0 || index >= paths.size()) {
            index = 0;
        }

        rotateButton.setOnClickListener(v -> {
            rotation = (rotation + 90) % 360;
            applyRotation();
            scheduleHideRotate();
        });

        root.setOnTouchListener(this::onTouch);
        showCurrent();
        handler.postDelayed(() -> hint.setVisibility(View.GONE), 3000);
    }

    private boolean onTouch(View view, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            int width = view.getWidth();
            fromEdge = downX < width * 0.16f || downX > width * 0.84f;
            return true;
        }
        if (action != MotionEvent.ACTION_UP) {
            return true;
        }

        float dx = event.getX() - downX;
        float dy = event.getY() - downY;
        int width = view.getWidth();
        float absX = Math.abs(dx);
        float absY = Math.abs(dy);

        if (fromEdge && absX > 50 && absX > absY * 1.3f) {
            boolean inward = (downX < width / 2f && dx > 0) || (downX > width / 2f && dx < 0);
            if (inward) {
                finish();
                return true;
            }
        }

        if (absX > 80 && absX > absY * 1.5f) {
            if (dx < 0 && index < paths.size() - 1) {
                index++;
                showCurrent();
            } else if (dx > 0 && index > 0) {
                index--;
                showCurrent();
            }
            return true;
        }

        if (absX < 16 && absY < 16) {
            rotateButton.setVisibility(View.VISIBLE);
            scheduleHideRotate();
        }
        return true;
    }

    private void scheduleHideRotate() {
        handler.removeCallbacks(hideRotate);
        handler.postDelayed(hideRotate, ROTATE_BUTTON_TIMEOUT);
    }

    private void showCurrent() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        Bitmap bitmap = BitmapLoader.decode(paths.get(index), width, height);
        if (bitmap == null) {
            Toast.makeText(this, "图片无法打开", Toast.LENGTH_SHORT).show();
            return;
        }
        if (shownBitmap != null && shownBitmap != baseBitmap) {
            shownBitmap.recycle();
        }
        baseBitmap = bitmap;
        shownBitmap = bitmap;
        rotation = 0;
        rotateButton.setVisibility(View.GONE);
        handler.removeCallbacks(hideRotate);
        fullImage.setImageBitmap(bitmap);
    }

    private void applyRotation() {
        if (baseBitmap == null) {
            return;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(baseBitmap, 0, 0,
                baseBitmap.getWidth(), baseBitmap.getHeight(), matrix, true);
        if (shownBitmap != null && shownBitmap != baseBitmap) {
            shownBitmap.recycle();
        }
        shownBitmap = rotated;
        fullImage.setImageBitmap(rotated);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
