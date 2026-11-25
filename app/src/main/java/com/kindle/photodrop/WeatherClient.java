package com.kindle.photodrop;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 台历天气数据：自动定位、城市搜索和 Open-Meteo 每日预报。 */
final class WeatherClient {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private WeatherClient() {
    }

    static final class Location {
        final double latitude;
        final double longitude;
        final String name;
        final String admin1;
        final String country;

        Location(double latitude, double longitude, String name) {
            this(latitude, longitude, name, "", "");
        }

        Location(double latitude, double longitude, String name,
                 String admin1, String country) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.name = name;
            this.admin1 = admin1 == null ? "" : admin1;
            this.country = country == null ? "" : country;
        }

        /** 候选列表里的展示文字：城市 + 省份 + 国家，过滤重复项。 */
        String displayName() {
            StringBuilder sb = new StringBuilder(name);
            if (!admin1.isEmpty() && !admin1.equals(name)) {
                sb.append(" · ").append(admin1);
            }
            if (!country.isEmpty() && !country.equals(name) && !country.equals(admin1)) {
                sb.append(" · ").append(country);
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return displayName();
        }
    }

    /** 根据当前公网出口 IP 粗略定位，失败时由调用方使用默认城市。 */
    static Location autoLocate() throws Exception {
        JSONObject obj = getJson("http://ip-api.com/json/?lang=zh-CN"
                + "&fields=status,city,regionName,lat,lon");
        if (!"success".equals(obj.optString("status"))) {
            throw new IOException("定位失败");
        }
        String name = obj.optString("city", "");
        if (name.isEmpty()) {
            name = obj.optString("regionName", "当前位置");
        }
        return new Location(obj.getDouble("lat"), obj.getDouble("lon"), name);
    }

    /**
     * 按城市名称查询多个候选（最多 8 个），供用户点选。
     * 用 Photon（OpenStreetMap）而非 Open-Meteo 自带 geocoding：后者对中国城市
     * 覆盖差（如搜不到江油、珠海返回山东同名村、兰州混入江西同名地点）；
     * Photon 数据来自 OSM，中文地名更准确。只保留 osm_key=place 的行政区/城镇，
     * 过滤车站、机场、大学、公司等 POI。
     */
    static List<Location> searchCities(String city) throws Exception {
        String query = city == null ? "" : city.trim();
        if (query.isEmpty()) {
            throw new IOException("城市不能为空");
        }
        String url = "https://photon.komoot.io/api/?q="
                + URLEncoder.encode(query, "UTF-8")
                + "&limit=20";
        JSONObject obj = getJson(url);
        JSONArray features = obj.optJSONArray("features");
        List<Location> locations = new ArrayList<>();
        if (features == null) {
            return locations;
        }
        for (int i = 0; i < features.length() && locations.size() < 8; i++) {
            JSONObject feature = features.optJSONObject(i);
            if (feature == null) {
                continue;
            }
            JSONObject props = feature.optJSONObject("properties");
            if (props == null || !"place".equals(props.optString("osm_key"))) {
                continue;
            }
            String name = props.optString("name", "");
            if (name.isEmpty()) {
                continue;
            }
            String state = props.optString("state", "");
            String country = props.optString("country", "");
            JSONObject geometry = feature.optJSONObject("geometry");
            JSONArray coords = geometry == null ? null : geometry.optJSONArray("coordinates");
            if (coords == null || coords.length() < 2) {
                continue;
            }
            double lon = coords.optDouble(0);
            double lat = coords.optDouble(1);
            // 同一城市在 OSM 里常有 region/city 两个节点，按名称+省份去重。
            boolean duplicate = false;
            for (Location existing : locations) {
                if (existing.name.equals(name) && existing.admin1.equals(state)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                locations.add(new Location(lat, lon, name, state, country));
            }
        }
        return locations;
    }

    /** 兼容旧调用：取第一个候选。 */
    static Location geocode(String city) throws Exception {
        List<Location> locations = searchCities(city);
        if (locations.isEmpty()) {
            throw new IOException("没有找到这个城市");
        }
        return locations.get(0);
    }

    /** 今天起 8 天的每日天气，覆盖「今天 + 未来一周」。 */
    static JSONObject forecast(double latitude, double longitude) throws Exception {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast"
                        + "?latitude=%.4f&longitude=%.4f"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                        + "&timezone=Asia%%2FShanghai&forecast_days=8",
                latitude, longitude);
        return getJson(url);
    }

    private static JSONObject getJson(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "KindleAssistant/1.0");
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            InputStream in = connection.getInputStream();
            try {
                return new JSONObject(new String(readAll(in), UTF8));
            } finally {
                in.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
