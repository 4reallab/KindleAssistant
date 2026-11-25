package com.kindle.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** 电子台历：一天一页，显示公历/农历/星期/节气与按日期范围变化的天气预报。 */
public class CalendarActivity extends Activity {

    private static final String PREFS = "calendar_state";
    private static final String KEY_LAT = "latitude";
    private static final String KEY_LON = "longitude";
    private static final String KEY_CITY = "city";
    private static final String KEY_FORECAST = "forecast";
    private static final String KEY_FORECAST_AT = "forecastAt";
    private static final long FORECAST_MAX_AGE = 3 * 60 * 60 * 1000L;

    private static final TimeZone CHINA = TimeZone.getTimeZone("Asia/Shanghai");
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private TextView dateText;
    private TextView weekdayText;
    private TextView timeText;
    private TextView lunarText;
    private TextView termText;
    private TextView forecastTitle;
    private TextView weatherHint;
    private LinearLayout forecastList;
    private LinearLayout todayWeatherBox;
    private TextView todayWeatherText;
    private TextView todayWeatherTemp;
    private Button cityButton;
    private Button todayButton;

    private SharedPreferences prefs;
    private GregorianCalendar today;
    private GregorianCalendar shown;
    private JSONObject forecast;
    private String cityName = "";

    /** MCP 需要刷新正在显示的台历页面。 */
    private static volatile CalendarActivity mcpInstance;

    private final SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);

    private static final long WEATHER_REFRESH_INTERVAL = 60 * 60 * 1000L;

    private final Handler handler = new Handler();
    private final Handler citySearchHandler = new Handler();
    private long weatherFetchedAt = 0;

    private final Runnable clockTicker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            // 对齐整分钟刷新，避免显示比系统状态栏滞后近 1 分钟。
            handler.postDelayed(this, millisToNextMinute());
        }
    };

    private static long millisToNextMinute() {
        return 60_000L - (System.currentTimeMillis() % 60_000L);
    }

    private final Runnable weatherTicker = new Runnable() {
        @Override
        public void run() {
            loadWeather(false);
            handler.postDelayed(this, WEATHER_REFRESH_INTERVAL);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        keyFormat.setTimeZone(CHINA);

        dateText = findViewById(R.id.dateText);
        weekdayText = findViewById(R.id.weekdayText);
        timeText = findViewById(R.id.timeText);
        lunarText = findViewById(R.id.lunarText);
        termText = findViewById(R.id.termText);
        forecastTitle = findViewById(R.id.forecastTitle);
        weatherHint = findViewById(R.id.weatherHint);
        forecastList = findViewById(R.id.forecastList);
        todayWeatherBox = findViewById(R.id.todayWeatherBox);
        todayWeatherText = findViewById(R.id.todayWeatherText);
        todayWeatherTemp = findViewById(R.id.todayWeatherTemp);
        cityButton = findViewById(R.id.cityButton);
        todayButton = findViewById(R.id.todayButton);

        today = startOfDay(new GregorianCalendar(CHINA));
        shown = (GregorianCalendar) today.clone();

        findViewById(R.id.prevDay).setOnClickListener(v -> moveDay(-1));
        findViewById(R.id.nextDay).setOnClickListener(v -> moveDay(1));
        todayButton.setOnClickListener(v -> {
            shown = (GregorianCalendar) today.clone();
            renderDate();
            renderWeather();
        });
        cityButton.setOnClickListener(v -> showCityDialog());
        findViewById(R.id.refreshWeather).setOnClickListener(v -> loadWeather(true));

        cityName = prefs.getString(KEY_CITY, "");
        updateCityButton();
        renderDate();

        String cached = prefs.getString(KEY_FORECAST, "");
        weatherFetchedAt = prefs.getLong(KEY_FORECAST_AT, 0);
        if (!cached.isEmpty() && System.currentTimeMillis() - weatherFetchedAt < FORECAST_MAX_AGE) {
            try {
                forecast = new JSONObject(cached);
            } catch (Exception ignored) {
                forecast = null;
            }
        }
        renderWeather();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mcpInstance = this;
        GregorianCalendar newToday = startOfDay(new GregorianCalendar(CHINA));
        if (!sameDay(newToday, today)) {
            today = newToday;
            shown = (GregorianCalendar) today.clone();
            renderDate();
            renderWeather();
        }
        updateClock();
        handler.post(clockTicker);
        // 每次唤醒（含首次进入）都刷新天气
        loadWeather(false);
        handler.removeCallbacks(weatherTicker);
        handler.postDelayed(weatherTicker, WEATHER_REFRESH_INTERVAL);
    }

    @Override
    protected void onPause() {
        mcpInstance = null;
        handler.removeCallbacks(clockTicker);
        handler.removeCallbacks(weatherTicker);
        super.onPause();
    }

    private void moveDay(int delta) {
        shown.add(GregorianCalendar.DATE, delta);
        renderDate();
        renderWeather();
    }

    private void renderDate() {
        dateText.setText(shown.get(GregorianCalendar.YEAR) + "年"
                + (shown.get(GregorianCalendar.MONTH) + 1) + "月"
                + shown.get(GregorianCalendar.DAY_OF_MONTH) + "日");
        weekdayText.setText(LunarCalendar.weekdayText(shown));
        lunarText.setText("农历 " + LunarCalendar.lunarText(shown));
        termText.setText("节气：" + LunarCalendar.solarTerm(shown));
        boolean isToday = sameDay(shown, today);
        todayButton.setVisibility(isToday ? View.GONE : View.VISIBLE);
        timeText.setVisibility(isToday ? View.VISIBLE : View.GONE);
        if (isToday) {
            updateClock();
        }
    }

    /** 仅当显示的是今天时刷新时钟，非今日页面不显示时间。 */
    private void updateClock() {
        if (timeText.getVisibility() == View.VISIBLE) {
            timeFormat.setTimeZone(CHINA);
            timeText.setText(timeFormat.format(new Date()));
        }
    }

    private void renderWeather() {
        forecastList.removeAllViews();
        weatherHint.setText("");
        todayWeatherBox.setVisibility(View.GONE);
        if (forecast == null) {
            forecastTitle.setText(getString(R.string.calendar_forecast_today));
            weatherHint.setText(R.string.calendar_weather_loading);
            return;
        }
        List<DayWeather> days = parseForecast(forecast);
        if (days.isEmpty()) {
            forecastTitle.setText(getString(R.string.calendar_forecast_today));
            weatherHint.setText(R.string.calendar_weather_error);
            return;
        }

        int offset = daysBetween(today, shown);
        if (offset == 0) {
            forecastTitle.setText(getString(R.string.calendar_forecast_today));
            // 今天天气放在日期时间区域左侧空白处
            DayWeather todayWeather = days.get(0);
            ((WeatherIconView) todayWeatherBox.findViewById(R.id.todayWeatherIcon))
                    .setCode(todayWeather.code);
            todayWeatherText.setText(todayWeather.description);
            todayWeatherTemp.setText(todayWeather.min + "～" + todayWeather.max + "°");
            todayWeatherBox.setVisibility(View.VISIBLE);
            // 未来一周只显示明天起的 7 天，今天已在左侧天气块展示。
            for (int i = 1; i < days.size(); i++) {
                addWeatherColumn(days.get(i));
            }
            return;
        }
        if (offset >= 1 && offset <= 7) {
            forecastTitle.setText(getString(R.string.calendar_forecast_day));
            String key = keyFormat.format(shown.getTime());
            for (DayWeather day : days) {
                if (day.date.equals(key)) {
                    addWeatherColumn(day);
                    return;
                }
            }
        }
        forecastTitle.setText(getString(R.string.calendar_forecast_day));
        weatherHint.setText(R.string.calendar_no_weather);
    }

    /** 未来天气按列展示：所有列 weight 均分占满一行，一屏内全部显示、无需滑动。 */
    private void addWeatherColumn(DayWeather day) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        int gap = getResources().getDimensionPixelSize(R.dimen.weather_card_gap);
        params.setMargins(gap, 0, gap, 0);
        column.setLayoutParams(params);
        column.setBackgroundResource(R.drawable.weather_cell);
        column.setPadding(0, 12, 0, 12);

        WeatherIconView icon = new WeatherIconView(this);
        int iconSize = getResources().getDimensionPixelSize(R.dimen.weather_icon_size);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        icon.setCode(day.code);
        column.addView(icon);

        TextView date = new TextView(this);
        date.setText(day.date.substring(5).replace("-", "/"));
        date.setTextColor(0xFF111111);
        date.setTextSize(13);
        date.setGravity(Gravity.CENTER);
        date.setPadding(0, 6, 0, 0);
        column.addView(date);

        TextView desc = new TextView(this);
        desc.setText(day.description);
        desc.setTextColor(0xFF333333);
        desc.setTextSize(14);
        desc.setGravity(Gravity.CENTER);
        column.addView(desc);

        TextView temp = new TextView(this);
        temp.setText(day.min + "～" + day.max + "°");
        temp.setTextColor(0xFF555555);
        temp.setTextSize(12);
        temp.setGravity(Gravity.CENTER);
        column.addView(temp);

        forecastList.addView(column);
    }

    private List<DayWeather> parseForecast(JSONObject json) {
        List<DayWeather> result = new ArrayList<>();
        JSONObject daily = json.optJSONObject("daily");
        if (daily == null) {
            return result;
        }
        JSONArray times = daily.optJSONArray("time");
        JSONArray codes = daily.optJSONArray("weather_code");
        JSONArray highs = daily.optJSONArray("temperature_2m_max");
        JSONArray lows = daily.optJSONArray("temperature_2m_min");
        if (times == null || codes == null || highs == null || lows == null) {
            return result;
        }
        int count = Math.min(times.length(), Math.min(codes.length(),
                Math.min(highs.length(), lows.length())));
        for (int i = 0; i < count; i++) {
            String date = times.optString(i, "");
            if (date.length() < 10) {
                continue;
            }
            int code = codes.optInt(i, 0);
            result.add(new DayWeather(date.substring(0, 10), code,
                    weatherText(code),
                    formatTemp(lows.optDouble(i, 0)),
                    formatTemp(highs.optDouble(i, 0))));
        }
        return result;
    }

    private static String formatTemp(double value) {
        if (Double.isNaN(value)) {
            return "--";
        }
        return String.valueOf(Math.round(value));
    }

    private void loadWeather(boolean force) {
        weatherHint.setText(R.string.calendar_weather_loading);
        new Thread(() -> {
            try {
                WeatherClient.Location location = loadLocation();
                if (location == null) {
                    location = WeatherClient.autoLocate();
                    saveLocation(location);
                }
                JSONObject json = WeatherClient.forecast(location.latitude, location.longitude);
                final JSONObject result = json;
                final String name = location.name;
                runOnUiThread(() -> {
                    forecast = result;
                    cityName = name;
                    updateCityButton();
                    weatherFetchedAt = System.currentTimeMillis();
                    prefs.edit().putString(KEY_FORECAST, result.toString())
                            .putLong(KEY_FORECAST_AT, weatherFetchedAt).apply();
                    renderWeather();
                });
            } catch (Exception e) {
                Log.e("PhotoDrop", "weather fetch failed", e);
                if (!force && forecast != null) {
                    runOnUiThread(() -> renderWeather());
                    return;
                }
                runOnUiThread(() -> {
                    if (forecast == null) {
                        weatherHint.setText(R.string.calendar_weather_error);
                    } else {
                        Toast.makeText(CalendarActivity.this,
                                R.string.calendar_weather_error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void showCityDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_city_search, null);
        EditText input = view.findViewById(R.id.cityInput);
        ListView candidateList = view.findViewById(R.id.cityCandidates);
        TextView candidateTitle = view.findViewById(R.id.cityCandidateTitle);
        input.setText(cityName);
        input.setSelection(input.getText().length());

        ArrayAdapter<WeatherClient.Location> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, new ArrayList<>());
        candidateList.setAdapter(adapter);
        final AlertDialog[] dialogRef = new AlertDialog[1];
        candidateList.setOnItemClickListener((parent, itemView, position, id) -> {
            WeatherClient.Location location = adapter.getItem(position);
            if (location != null) {
                saveLocation(location);
                cityName = location.name;
                updateCityButton();
                loadWeather(true);
                dialogRef[0].dismiss();
            }
        });

        // 输入停顿 400ms 后自动搜索候选，避免每敲一个字都发请求。
        final long[] searchVersion = {0};
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                citySearchHandler.removeCallbacksAndMessages(null);
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    adapter.clear();
                    adapter.notifyDataSetChanged();
                    candidateTitle.setText(R.string.calendar_city_candidates);
                    return;
                }
                citySearchHandler.postDelayed(() -> searchCityCandidates(
                        query, adapter, candidateTitle, searchVersion), 400);
            }
        });

        dialogRef[0] = new AlertDialog.Builder(this)
                .setTitle(R.string.calendar_input_city)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .show();

        if (!cityName.isEmpty()) {
            searchCityCandidates(cityName, adapter, candidateTitle, searchVersion);
        }
    }

    /** 异步拉取城市候选，用版本号丢弃过期结果。 */
    private void searchCityCandidates(String query,
                                      ArrayAdapter<WeatherClient.Location> adapter,
                                      TextView title, long[] version) {
        final long myVersion = ++version[0];
        title.setText(R.string.calendar_searching);
        new Thread(() -> {
            try {
                final List<WeatherClient.Location> found = WeatherClient.searchCities(query);
                runOnUiThread(() -> {
                    if (myVersion != version[0]) {
                        return;
                    }
                    adapter.clear();
                    adapter.addAll(found);
                    adapter.notifyDataSetChanged();
                    title.setText(found.isEmpty()
                            ? R.string.calendar_city_no_match
                            : R.string.calendar_city_candidates);
                });
            } catch (Exception e) {
                Log.e("PhotoDrop", "city search failed", e);
                runOnUiThread(() -> {
                    if (myVersion != version[0]) {
                        return;
                    }
                    adapter.clear();
                    adapter.notifyDataSetChanged();
                    title.setText(R.string.calendar_city_no_match);
                });
            }
        }).start();
    }

    private WeatherClient.Location loadLocation() {
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LON)) {
            return null;
        }
        return new WeatherClient.Location(
                Double.longBitsToDouble(prefs.getLong(KEY_LAT, 0)),
                Double.longBitsToDouble(prefs.getLong(KEY_LON, 0)),
                prefs.getString(KEY_CITY, ""));
    }

    private void saveLocation(WeatherClient.Location location) {
        prefs.edit()
                .putLong(KEY_LAT, Double.doubleToLongBits(location.latitude))
                .putLong(KEY_LON, Double.doubleToLongBits(location.longitude))
                .putString(KEY_CITY, location.name)
                .apply();
    }

    private void updateCityButton() {
        String name = cityName.isEmpty() ? getString(R.string.calendar_input_city) : cityName;
        cityButton.setText(getString(R.string.calendar_city, name));
    }

    // ---- MCP 接口：设置城市 / 强制刷新天气 ----

    static boolean mcpSetCity(Context context, String city, double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            return false;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_CITY, city)
                .putLong(KEY_LAT, Double.doubleToLongBits(latitude))
                .putLong(KEY_LON, Double.doubleToLongBits(longitude))
                .apply();
        CalendarActivity current = mcpInstance;
        if (current != null) {
            current.runOnUiThread(() -> {
                CalendarActivity activity = mcpInstance;
                if (activity == null) {
                    return;
                }
                activity.cityName = city;
                activity.updateCityButton();
                activity.loadWeather(true);
            });
        }
        return true;
    }

    static boolean mcpRefresh() {
        CalendarActivity current = mcpInstance;
        if (current == null) {
            return false;
        }
        current.runOnUiThread(() -> {
            CalendarActivity activity = mcpInstance;
            if (activity != null) {
                activity.loadWeather(true);
            }
        });
        return true;
    }

    private static GregorianCalendar startOfDay(GregorianCalendar source) {
        GregorianCalendar day = new GregorianCalendar(CHINA);
        day.clear();
        day.set(source.get(GregorianCalendar.YEAR),
                source.get(GregorianCalendar.MONTH),
                source.get(GregorianCalendar.DAY_OF_MONTH));
        return day;
    }

    private static boolean sameDay(GregorianCalendar a, GregorianCalendar b) {
        return a.get(GregorianCalendar.YEAR) == b.get(GregorianCalendar.YEAR)
                && a.get(GregorianCalendar.MONTH) == b.get(GregorianCalendar.MONTH)
                && a.get(GregorianCalendar.DAY_OF_MONTH) == b.get(GregorianCalendar.DAY_OF_MONTH);
    }

    private static int daysBetween(GregorianCalendar from, GregorianCalendar to) {
        GregorianCalendar a = new GregorianCalendar(UTC);
        a.clear();
        a.set(from.get(GregorianCalendar.YEAR),
                from.get(GregorianCalendar.MONTH),
                from.get(GregorianCalendar.DAY_OF_MONTH));
        GregorianCalendar b = new GregorianCalendar(UTC);
        b.clear();
        b.set(to.get(GregorianCalendar.YEAR),
                to.get(GregorianCalendar.MONTH),
                to.get(GregorianCalendar.DAY_OF_MONTH));
        return (int) ((b.getTimeInMillis() - a.getTimeInMillis()) / 86400000L);
    }

    private static String weatherText(int code) {
        switch (code) {
            case 0:
                return "晴";
            case 1:
                return "少云";
            case 2:
                return "多云";
            case 3:
                return "阴";
            case 45:
            case 48:
                return "雾";
            case 51:
            case 53:
            case 55:
                return "小雨";
            case 56:
            case 57:
            case 66:
            case 67:
                return "冻雨";
            case 61:
                return "小雨";
            case 63:
                return "中雨";
            case 65:
                return "大雨";
            case 71:
                return "小雪";
            case 73:
                return "中雪";
            case 75:
                return "大雪";
            case 77:
                return "雪粒";
            case 80:
                return "阵雨";
            case 81:
                return "中阵雨";
            case 82:
                return "强阵雨";
            case 85:
                return "小阵雪";
            case 86:
                return "大阵雪";
            case 95:
                return "雷阵雨";
            case 96:
            case 99:
                return "雷暴冰雹";
            case 97:
                return "强雷暴";
            default:
                return "天气" + code;
        }
    }

    private static final class DayWeather {
        final String date;
        final int code;
        final String description;
        final String min;
        final String max;

        DayWeather(String date, int code, String description, String min, String max) {
            this.date = date;
            this.code = code;
            this.description = description;
            this.min = min;
            this.max = max;
        }
    }
}
