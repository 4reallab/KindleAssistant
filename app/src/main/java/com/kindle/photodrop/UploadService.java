package com.kindle.photodrop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 前台服务：在局域网内提供 HTTP 上传页面，收到文件后写入图片目录并自动打开。 */
public class UploadService extends Service {

    public static final String ACTION_START = "com.kindle.photodrop.START";
    public static final String ACTION_STOP = "com.kindle.photodrop.STOP";
    public static final String ACTION_STATUS = "com.kindle.photodrop.STATUS";
    public static final String BROADCAST_IMAGE = "com.kindle.photodrop.IMAGE_RECEIVED";
    public static final int PORT = 8088;

    private static final String TAG = "PhotoDrop";
    private static final String CHANNEL_ID = "photo_drop_upload";
    private static final int NOTIFICATION_ID = 1001;
    private static final int MAX_BODY = 64 * 1024 * 1024;
    private static final Charset ASCII = Charset.forName("US-ASCII");
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Pattern FILENAME_PATTERN = Pattern.compile("filename\\s*=\\s*\"([^\"]*)\"");

    private final Object lock = new Object();
    private ServerSocket server;
    private ExecutorService pool;
    private volatile boolean running;
    private volatile boolean foreground;
    private volatile String url = "";
    private volatile String ip = "";

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopServer();
            stopSelf();
            return START_NOT_STICKY;
        }
        startServer();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    private void startServer() {
        synchronized (lock) {
            if (running) {
                broadcastStatus();
                return;
            }
            ip = NetworkHelper.getLanAddress(this);
            url = ip == null ? "" : "http://" + ip + ":" + PORT + "/";
            pool = Executors.newCachedThreadPool();
            startForeground(NOTIFICATION_ID, buildNotification(url));
            foreground = true;
            new Thread(this::acceptLoop, "kindle-assistant-http").start();
        }
    }

    private void acceptLoop() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(PORT));
            synchronized (lock) {
                server = socket;
                running = true;
            }
            Log.i(TAG, "HTTP server listening on " + PORT + " url=" + url);
            broadcastStatus();
            notifyUrl();
            while (running) {
                final Socket client = socket.accept();
                pool.execute(() -> handleClient(client));
            }
        } catch (IOException e) {
            Log.e(TAG, "HTTP server failed: " + e.getMessage());
            url = "";
        } finally {
            synchronized (lock) {
                server = null;
                running = false;
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
            broadcastStatus();
            stopSelf();
        }
    }

    private void stopServer() {
        synchronized (lock) {
            running = false;
            if (server != null) {
                try {
                    server.close();
                } catch (IOException ignored) {
                }
                server = null;
            }
            if (pool != null) {
                pool.shutdownNow();
                pool = null;
            }
        }
        if (foreground) {
            stopForeground(true);
            foreground = false;
        }
        broadcastStatus();
    }

    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(60000);
            InputStream in = new BufferedInputStream(client.getInputStream());
            OutputStream out = new BufferedOutputStream(client.getOutputStream());

            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] tokens = requestLine.split(" ");
            String method = tokens.length > 0 ? tokens[0].toUpperCase(Locale.US) : "GET";
            String path = tokens.length > 1 ? tokens[1] : "/";
            String query = "";
            int question = path.indexOf('?');
            if (question >= 0) {
                query = path.substring(question + 1);
                path = path.substring(0, question);
            }

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(Locale.US),
                            line.substring(colon + 1).trim());
                }
            }

            if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
                respond(out, 200, "text/html; charset=utf-8", WebPages.portal());
            } else if ("GET".equals(method)
                    && ("/upload.html".equals(path) || "/photos".equals(path))) {
                respond(out, 200, "text/html; charset=utf-8", UPLOAD_PAGE);
            } else if ("GET".equals(method)
                    && ("/todo".equals(path) || "/todo.html".equals(path))) {
                respond(out, 200, "text/html; charset=utf-8", WebPages.todo(query));
            } else if ("GET".equals(method) && "/status".equals(path)) {
                respond(out, 200, "text/plain; charset=utf-8",
                        "running=" + running + "\nurl=" + url
                                + "\nimages=" + ImageStore.listImages().size()
                                + "\ntodos=" + TodoStore.count(this));
            } else if ("POST".equals(method) && "/upload".equals(path)) {
                handleUpload(in, out, headers);
            } else if ("POST".equals(method) && path.startsWith("/todo/")) {
                handleTodoApi(in, out, headers, path);
            } else if ("POST".equals(method)) {
                respond(out, 404, "text/plain; charset=utf-8", "Not found");
            } else {
                respond(out, 405, "text/plain; charset=utf-8", "Method not allowed");
            }
        } catch (Exception e) {
            Log.w(TAG, "client error: " + e.getMessage());
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleUpload(InputStream in, OutputStream out, Map<String, String> headers) throws IOException {
        int length;
        try {
            length = Integer.parseInt(headers.get("content-length"));
        } catch (Exception e) {
            respond(out, 411, "text/plain; charset=utf-8", "Content-Length required");
            return;
        }
        if (length <= 0 || length > MAX_BODY) {
            respond(out, 413, "text/plain; charset=utf-8", "文件过大（上限 64 MB）");
            return;
        }
        byte[] body = readFully(in, length);

        String contentType = headers.get("content-type");
        String boundary = extractBoundary(contentType);
        if (boundary == null) {
            respond(out, 400, "text/plain; charset=utf-8", "缺少 multipart boundary");
            return;
        }

        List<Part> parts = parseMultipart(body, boundary);
        List<String> saved = new ArrayList<>();
        for (Part part : parts) {
            if (part.filename == null || part.filename.isEmpty() || part.data.length == 0) {
                continue;
            }
            try {
                saved.add(ImageStore.save(this, part.data, part.filename, part.contentType).getAbsolutePath());
            } catch (IOException e) {
                Log.e(TAG, "save failed: " + e.getMessage());
            }
        }

        if (saved.isEmpty()) {
            respond(out, 200, "text/plain; charset=utf-8", "没有收到图片，请重新选择后上传");
            return;
        }

        respond(out, 200, "text/plain; charset=utf-8",
                "已接收 " + saved.size() + " 个文件，已保存到 Pictures/KindlePhotoDrop");

        Intent broadcast = new Intent(BROADCAST_IMAGE).setPackage(getPackageName());
        broadcast.putStringArrayListExtra("paths", new ArrayList<>(saved));
        sendBroadcast(broadcast);

        openViewer(saved);
    }

    private void handleTodoApi(InputStream in, OutputStream out, Map<String, String> headers,
                               String path) throws IOException {
        int length;
        try {
            length = Integer.parseInt(headers.get("content-length"));
        } catch (Exception e) {
            respond(out, 411, "text/plain; charset=utf-8", "Content-Length required");
            return;
        }
        if (length <= 0 || length > 65536) {
            respond(out, 413, "text/plain; charset=utf-8", "请求过大");
            return;
        }
        Map<String, String> form = parseForm(new String(readFully(in, length), UTF8));
        boolean ok = false;
        long id = parseLong(form.get("id"));
        if ("/todo/add".equals(path)) {
            ok = TodoStore.add(this, form.get("title")) > 0;
        } else if ("/todo/update".equals(path)) {
            ok = TodoStore.update(this, id, form.get("title"));
        } else if ("/todo/delete".equals(path)) {
            ok = "1".equals(form.get("permanent"))
                    ? TodoStore.remove(this, id)
                    : TodoStore.setStatus(this, id, TodoStore.DELETED);
        } else if ("/todo/status".equals(path)) {
            ok = TodoStore.setStatus(this, id, (int) parseLong(form.get("status")));
        }
        int tab = (int) parseLong(form.get("tab"));
        if (tab < 0 || tab > 2) {
            tab = 0;
        }
        redirect(out, "/todo?tab=" + tab);
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new HashMap<>();
        for (String pair : body.split("&")) {
            int equal = pair.indexOf('=');
            if (equal <= 0) {
                continue;
            }
            try {
                String key = URLDecoder.decode(pair.substring(0, equal), "UTF-8");
                String value = URLDecoder.decode(pair.substring(equal + 1), "UTF-8");
                form.put(key, value);
            } catch (Exception ignored) {
            }
        }
        return form;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return -1;
        }
    }

    private void openViewer(List<String> paths) {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                PowerManager.WakeLock lockScreen = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "PhotoDrop:show_image");
                lockScreen.acquire(15000);
            }
            Intent intent = new Intent(this, ViewerActivity.class);
            intent.putStringArrayListExtra("paths", new ArrayList<>(paths));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "open viewer failed: " + e.getMessage());
        }
    }

    private void notifyUrl() {
        if (foreground) {
            startForeground(NOTIFICATION_ID, buildNotification(url));
        }
    }

    private void broadcastStatus() {
        Intent intent = new Intent(ACTION_STATUS).setPackage(getPackageName());
        intent.putExtra("running", running);
        intent.putExtra("url", url);
        intent.putExtra("ip", ip);
        sendBroadcast(intent);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, getString(R.string.upload_channel), NotificationManager.IMPORTANCE_LOW);
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(text.isEmpty() ? "等待网络连接…" : text)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r' && buffer.size() < 8192) {
                buffer.write(b);
            }
        }
        if (b == -1 && buffer.size() == 0) {
            return null;
        }
        return buffer.toString("UTF-8");
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(length);
        byte[] chunk = new byte[8192];
        int remaining = length;
        while (remaining > 0) {
            int read = in.read(chunk, 0, Math.min(chunk.length, remaining));
            if (read < 0) {
                break;
            }
            buffer.write(chunk, 0, read);
            remaining -= read;
        }
        return buffer.toByteArray();
    }

    private static void respond(OutputStream out, int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(UTF8);
        String status = code == 200 ? "OK" : "Error";
        String header = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(ASCII));
        out.write(bytes);
        out.flush();
    }

    private static void redirect(OutputStream out, String location) throws IOException {
        String header = "HTTP/1.1 303 See Other\r\n"
                + "Location: " + location + "\r\n"
                + "Content-Length: 0\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(ASCII));
        out.flush();
    }

    private static String extractBoundary(String contentType) {
        if (contentType == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("boundary=([^;]+)", Pattern.CASE_INSENSITIVE)
                .matcher(contentType);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).trim().replace("\"", "");
    }

    private static List<Part> parseMultipart(byte[] body, String boundary) {
        List<Part> parts = new ArrayList<>();
        byte[] delimiter = ("--" + boundary).getBytes(ASCII);
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i <= body.length - delimiter.length; i++) {
            if (matches(body, i, delimiter)) {
                positions.add(i);
                i += delimiter.length - 1;
            }
        }
        for (int i = 0; i + 1 < positions.size(); i++) {
            int start = positions.get(i) + delimiter.length;
            if (start + 1 < body.length && body[start] == '-' && body[start + 1] == '-') {
                break;
            }
            while (start < body.length && (body[start] == '\r' || body[start] == '\n')) {
                start++;
            }
            int end = positions.get(i + 1);
            if (end >= 2 && body[end - 2] == '\r' && body[end - 1] == '\n') {
                end -= 2;
            }
            int separator = indexOf(body, start, end, "\r\n\r\n".getBytes(ASCII));
            if (separator < 0 || separator + 4 > end) {
                continue;
            }
            String headers = new String(body, start, separator - start, UTF8);
            String name = headerValue(headers, "Content-Disposition");
            String filename = null;
            if (name != null) {
                Matcher matcher = FILENAME_PATTERN.matcher(name);
                if (matcher.find()) {
                    filename = matcher.group(1);
                }
            }
            String partType = headerValue(headers, "Content-Type");
            byte[] data = Arrays.copyOfRange(body, separator + 4, end);
            parts.add(new Part(filename, partType, data));
        }
        return parts;
    }

    private static boolean matches(byte[] data, int offset, byte[] needle) {
        for (int i = 0; i < needle.length; i++) {
            if (data[offset + i] != needle[i]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] data, int from, int to, byte[] needle) {
        for (int i = from; i <= to - needle.length; i++) {
            if (matches(data, i, needle)) {
                return i;
            }
        }
        return -1;
    }

    private static String headerValue(String headers, String key) {
        for (String line : headers.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(key)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static final class Part {
        final String filename;
        final String contentType;
        final byte[] data;

        Part(String filename, String contentType, byte[] data) {
            this.filename = filename;
            this.contentType = contentType;
            this.data = data;
        }
    }

    private static final String UPLOAD_PAGE =
            "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                    + "<title>上传图片到 Kindle</title><style>"
                    + "*{box-sizing:border-box}"
                    + "body{margin:0;min-height:100vh;background:#eef1f4;color:#1a1a1a;"
                    + "font-family:system-ui,-apple-system,'Segoe UI',sans-serif;"
                    + "display:flex;align-items:flex-start;justify-content:center;padding:28px 16px}"
                    + ".card{width:100%;max-width:460px;background:#fff;border-radius:16px;"
                    + "padding:26px;box-shadow:0 8px 30px rgba(0,0,0,.08)}"
                    + "h1{margin:0 0 6px;font-size:23px;letter-spacing:.5px}"
                    + ".sub{margin:0 0 22px;color:#6b7280;font-size:14px}"
                    + ".drop{display:flex;flex-direction:column;align-items:center;justify-content:center;"
                    + "border:2px dashed #c7cbd1;border-radius:14px;background:#fafbfc;"
                    + "padding:34px 18px;text-align:center;cursor:pointer;transition:.15s}"
                    + ".drop:hover,.drop.over{border-color:#111;background:#f3f4f6}"
                    + ".drop svg{width:46px;height:46px;stroke:#111;fill:none;stroke-width:1.7}"
                    + ".drop b{display:block;margin-top:12px;font-size:17px}"
                    + ".drop span{display:block;margin-top:6px;color:#6b7280;font-size:13px}"
                    + "input[type=file]{display:none}"
                    + "#selected{margin:12px 2px 0;font-size:14px;color:#374151;min-height:20px}"
                    + "button{width:100%;margin-top:18px;padding:14px;border:0;border-radius:10px;"
                    + "background:#111;color:#fff;font-size:16px;font-weight:600;cursor:pointer}"
                    + "button:disabled{background:#9ca3af;cursor:default}"
                    + "#bar{height:4px;margin-top:12px;border-radius:4px;background:#e5e7eb;overflow:hidden;display:none}"
                    + "#fill{height:100%;width:0;background:#111;transition:width .1s}"
                    + "#msg{margin-top:16px;padding:12px 14px;border-radius:10px;font-size:14px;"
                    + "background:#f3f4f6;color:#374151;display:none;line-height:1.5}"
                    + "#msg.ok{background:#e8f5e9;color:#1b5e20}"
                    + "#msg.err{background:#fdecea;color:#b3261e}"
                    + "</style></head><body><div class=\"card\">"
                    + "<h1>上传图片到 Kindle</h1>"
                    + "<p class=\"sub\">选好后点上传，可一次发送多张</p>"
                    + "<form id=\"f\" method=\"post\" action=\"/upload\" enctype=\"multipart/form-data\">"
                    + "<label class=\"drop\" id=\"drop\" for=\"file\">"
                    + "<svg viewBox=\"0 0 24 24\"><rect x=\"3\" y=\"4\" width=\"18\" height=\"16\" rx=\"2\"/>"
                    + "<circle cx=\"8.5\" cy=\"9\" r=\"1.5\"/><path d=\"M4 17l5-5 3.5 3.5 3-3L20 17\"/></svg>"
                    + "<b>点击选择图片</b><span>或直接把图片拖到这里</span></label>"
                    + "<input id=\"file\" type=\"file\" name=\"files\" accept=\"image/*\" multiple>"
                    + "<div id=\"selected\">尚未选择图片</div>"
                    + "<div id=\"bar\"><div id=\"fill\"></div></div>"
                    + "<button id=\"send\" type=\"submit\" disabled>上传到 Kindle</button>"
                    + "</form><div id=\"msg\"></div></div><script>"
                    + "var f=document.getElementById('f'),file=document.getElementById('file'),"
                    + "drop=document.getElementById('drop'),sel=document.getElementById('selected'),"
                    + "btn=document.getElementById('send'),msg=document.getElementById('msg'),"
                    + "bar=document.getElementById('bar'),fill=document.getElementById('fill');"
                    + "function selected(){var n=file.files.length;sel.textContent=n?('已选择 '+n+' 张图片'):'尚未选择图片';"
                    + "btn.disabled=!n;msg.style.display='none';}"
                    + "file.onchange=selected;"
                    + "drop.ondragover=function(e){e.preventDefault();drop.classList.add('over')};"
                    + "drop.ondragleave=function(){drop.classList.remove('over')};"
                    + "drop.ondrop=function(e){e.preventDefault();drop.classList.remove('over');"
                    + "file.files=e.dataTransfer.files;selected()};"
                    + "f.onsubmit=function(e){e.preventDefault();if(!file.files.length)return;"
                    + "btn.disabled=true;msg.style.display='none';msg.className='';bar.style.display='block';"
                    + "fill.style.width='0%';var x=new XMLHttpRequest();"
                    + "x.open('POST','/upload');"
                    + "x.upload.onprogress=function(p){if(p.lengthComputable)fill.style.width=(p.loaded/p.total*100)+'%'};"
                    + "x.onload=function(){bar.style.display='block';fill.style.width='100%';"
                    + "msg.textContent=x.responseText;msg.className=x.status===200?'ok':'err';"
                    + "msg.style.display='block';btn.disabled=false;file.value='';selected()};"
                    + "x.onerror=function(){bar.style.display='none';msg.textContent='上传失败，请检查 Wi-Fi';"
                    + "msg.className='err';msg.style.display='block';btn.disabled=false};"
                    + "x.send(new FormData(f))};"
                    + "</script></body></html>";
}
