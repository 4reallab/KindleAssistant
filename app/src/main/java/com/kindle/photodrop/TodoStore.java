package com.kindle.photodrop;

import android.content.Context;
import android.content.Intent;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 待办事项的持久化存储。所有增删改都写到应用内部文件，Web 端与设备端共享同一份数据。 */
final class TodoStore {

    static final int PENDING = 0;
    static final int DONE = 1;
    static final int DELETED = 2;
    static final String ACTION_CHANGED = "com.kindle.photodrop.TODO_CHANGED";

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String FILE_NAME = "todos.json";
    private static final List<Todo> ITEMS = new ArrayList<>();
    private static long nextId = 1;
    private static boolean loaded;

    static final class Todo {
        final long id;
        final String title;
        final int status;
        final long createdAt;
        final long completedAt;
        final long deletedAt;
        final long updatedAt;
        final long elapsedMillis;

        Todo(long id, String title, int status, long createdAt,
             long completedAt, long deletedAt, long updatedAt, long elapsedMillis) {
            this.id = id;
            this.title = title;
            this.status = status;
            this.createdAt = createdAt;
            this.completedAt = completedAt;
            this.deletedAt = deletedAt;
            this.updatedAt = updatedAt;
            this.elapsedMillis = elapsedMillis;
        }
    }

    private TodoStore() {
    }

    static synchronized void load(Context context) {
        if (loaded) {
            return;
        }
        loaded = true;
        ITEMS.clear();
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
        if (!file.getBaseFile().exists()) {
            return;
        }
        try {
            byte[] data = readAll(file.openRead());
            JSONArray array = new JSONArray(new String(data, UTF8));
            long maxId = 0;
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                long id = obj.optLong("id", 0);
                String title = obj.optString("title", "");
                int status = obj.optInt("status", PENDING);
                if (id <= 0 || title.length() == 0) {
                    continue;
                }
                if (status < PENDING || status > DELETED) {
                    status = PENDING;
                }
                long createdAt = obj.optLong("createdAt", 0);
                long updatedAt = obj.optLong("updatedAt", 0);
                ITEMS.add(new Todo(id, title, status, createdAt,
                        obj.optLong("completedAt", status == DONE ? updatedAt : 0),
                        obj.optLong("deletedAt", status == DELETED ? updatedAt : 0),
                        updatedAt, obj.optLong("elapsedMillis", 0)));
                maxId = Math.max(maxId, id);
            }
            nextId = maxId + 1;
        } catch (Exception e) {
            ITEMS.clear();
            nextId = 1;
        }
    }

    static synchronized List<Todo> list(int status) {
        load(App.instance());
        List<Todo> result = new ArrayList<>();
        for (Todo todo : ITEMS) {
            if (todo.status == status) {
                result.add(todo);
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return result;
    }

    static synchronized Todo get(long id) {
        load(App.instance());
        for (Todo todo : ITEMS) {
            if (todo.id == id) {
                return todo;
            }
        }
        return null;
    }

    static synchronized long add(Context context, String title) {
        load(context);
        String clean = title == null ? "" : title.trim();
        if (clean.length() == 0) {
            return -1;
        }
        long now = System.currentTimeMillis();
        Todo todo = new Todo(nextId++, clean, PENDING, now, 0, 0, now, 0);
        ITEMS.add(todo);
        save(context);
        changed(context);
        return todo.id;
    }

    static synchronized boolean update(Context context, long id, String title) {
        load(context);
        String clean = title == null ? "" : title.trim();
        if (clean.length() == 0) {
            return false;
        }
        for (int i = 0; i < ITEMS.size(); i++) {
            Todo old = ITEMS.get(i);
            if (old.id == id) {
                ITEMS.set(i, new Todo(old.id, clean, old.status, old.createdAt,
                        old.completedAt, old.deletedAt, System.currentTimeMillis(),
                        old.elapsedMillis));
                save(context);
                changed(context);
                return true;
            }
        }
        return false;
    }

    static synchronized boolean setStatus(Context context, long id, int status) {
        load(context);
        if (status < PENDING || status > DELETED) {
            return false;
        }
        for (int i = 0; i < ITEMS.size(); i++) {
            Todo old = ITEMS.get(i);
            if (old.id == id) {
                long now = System.currentTimeMillis();
                long completedAt = status == DONE ? now : old.completedAt;
                long deletedAt = status == DELETED ? now : old.deletedAt;
                ITEMS.set(i, new Todo(old.id, old.title, status, old.createdAt,
                        completedAt, deletedAt, now, old.elapsedMillis));
                save(context);
                changed(context);
                return true;
            }
        }
        return false;
    }

    /** 把一次计时耗时累加到待办上。 */
    static synchronized boolean addElapsed(Context context, long id, long millis) {
        load(context);
        if (millis <= 0) {
            return false;
        }
        for (int i = 0; i < ITEMS.size(); i++) {
            Todo old = ITEMS.get(i);
            if (old.id == id) {
                ITEMS.set(i, new Todo(old.id, old.title, old.status, old.createdAt,
                        old.completedAt, old.deletedAt, System.currentTimeMillis(),
                        old.elapsedMillis + millis));
                save(context);
                changed(context);
                return true;
            }
        }
        return false;
    }

    static synchronized boolean remove(Context context, long id) {
        load(context);
        for (int i = 0; i < ITEMS.size(); i++) {
            if (ITEMS.get(i).id == id) {
                ITEMS.remove(i);
                save(context);
                changed(context);
                return true;
            }
        }
        return false;
    }

    static synchronized int count(Context context) {
        load(context);
        return ITEMS.size();
    }

    static String statusName(int status) {
        if (status == DONE) {
            return "已完成";
        }
        if (status == DELETED) {
            return "已删除";
        }
        return "未完成";
    }

    /** 列表小字：三个标签页都显示添加时间，已完成/已删除再附带状态时间。 */
    static String timeText(Todo todo) {
        String prefix;
        long time;
        if (todo.status == DONE) {
            prefix = "完成：";
            time = todo.completedAt;
        } else if (todo.status == DELETED) {
            prefix = "删除：";
            time = todo.deletedAt;
        } else {
            prefix = "添加：";
            time = todo.createdAt;
        }
        if (time <= 0) {
            time = todo.createdAt;
        }
        String text = prefix + formatTime(time);
        if (todo.status != PENDING && todo.createdAt > 0) {
            text += "　添加：" + formatTime(todo.createdAt);
        }
        if (todo.elapsedMillis > 0) {
            text += "　累计耗时：" + durationText(todo.elapsedMillis);
        }
        return text;
    }

    /** 耗时为静态展示（非每秒刷新），保留秒以便精确查看。 */
    static String durationText(long millis) {
        if (millis <= 0) {
            return "00:00:00";
        }
        long seconds = millis / 1000;
        long days = seconds / 86400;
        seconds -= days * 86400;
        long hours = seconds / 3600;
        seconds -= hours * 3600;
        long minutes = seconds / 60;
        seconds -= minutes * 60;
        String hms = String.format(Locale.CHINA, "%02d:%02d:%02d", hours, minutes, seconds);
        return days > 0 ? days + "天 " + hms : hms;
    }

    private static String formatTime(long time) {
        if (time <= 0) {
            return "时间未知";
        }
        // 每次新建格式对象，避免 Web 服务多线程共享 SimpleDateFormat。
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date(time));
    }

    private static void save(Context context) {
        JSONArray array = new JSONArray();
        for (Todo todo : ITEMS) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", todo.id);
                obj.put("title", todo.title);
                obj.put("status", todo.status);
                obj.put("createdAt", todo.createdAt);
                obj.put("completedAt", todo.completedAt);
                obj.put("deletedAt", todo.deletedAt);
                obj.put("updatedAt", todo.updatedAt);
                obj.put("elapsedMillis", todo.elapsedMillis);
                array.put(obj);
            } catch (Exception ignored) {
            }
        }
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(array.toString().getBytes(UTF8));
            file.finishWrite(out);
        } catch (IOException e) {
            if (out != null) {
                file.failWrite(out);
            }
        }
    }

    private static byte[] readAll(FileInputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
        }
        in.close();
        return buffer.toByteArray();
    }

    private static void changed(Context context) {
        context.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));
    }
}
