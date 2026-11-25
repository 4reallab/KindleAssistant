package com.kindle.photodrop;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 负责把收到的文件永久写入公共「图片」目录，并列出已保存的图片。 */
final class ImageStore {

    static final String SUBDIR = "KindlePhotoDrop";
    static final String PREFIX = "KindleDrop_";
    private static final String TAG = "PhotoDrop";
    private static final String[] IMAGE_EXT = {
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"
    };

    private ImageStore() {
    }

    static File getPicturesDir(Context context) {
        File publicDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), SUBDIR);
        if (publicDir.exists() || publicDir.mkdirs()) {
            return publicDir;
        }
        File fallback = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (fallback == null) {
            fallback = context.getFilesDir();
        }
        if (!fallback.exists()) {
            fallback.mkdirs();
        }
        return fallback;
    }

    static File save(Context context, byte[] data, String originalName, String contentType) throws IOException {
        String extension = extensionOf(originalName, contentType);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        String name = stamp + "_" + safeBaseName(originalName) + extension;
        String mime = contentType == null || contentType.isEmpty() ? "image/*" : contentType;

        // 优先由系统媒体服务写入公共 Pictures：这台 Readom 的普通应用进程没有直接写公共目录的权限。
        File publicDir = ensurePublicDir(context);
        boolean inSubdir = publicDir != null;
        File picturesRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File target = inSubdir ? new File(publicDir, name) : new File(picturesRoot, PREFIX + name);
        Uri uri = insertViaMediaStore(context, target.getName(), target, mime, data);
        if (uri != null) {
            String realPath = queryDataPath(context, uri);
            File result = realPath != null ? new File(realPath) : target;
            Log.i(TAG, "saved via MediaStore: " + result.getAbsolutePath());
            return result;
        }

        // 媒体库不可用时退回应用自己的图片目录，保证不丢文件。
        File dir = getPicturesDir(context);
        File fallbackFile = uniqueFile(dir, name);
        try {
            writeDirect(fallbackFile, data);
            scan(context, fallbackFile, mime);
            Log.i(TAG, "saved directly: " + fallbackFile.getAbsolutePath());
            return fallbackFile;
        } catch (IOException e) {
            Log.e(TAG, "save failed: " + e.getMessage());
            throw e;
        }
    }

    private static File ensurePublicDir(Context context) {
        File dir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), SUBDIR);
        if (dir.exists() || dir.mkdirs()) {
            return dir;
        }
        try {
            Uri pictures = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:Pictures");
            Uri created = DocumentsContract.createDocument(context.getContentResolver(), pictures,
                    DocumentsContract.Document.MIME_TYPE_DIR, SUBDIR);
            if (created != null && dir.exists()) {
                return dir;
            }
        } catch (Exception e) {
            Log.w(TAG, "create public dir via DocumentsContract failed: " + e.getMessage());
        }
        return null;
    }

    private static void writeDirect(File target, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(target);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    private static Uri insertViaMediaStore(Context context, String name, File target,
                                           String mime, byte[] data) {
        try {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, mime);
            values.put(MediaStore.Images.Media.DATA, target.getAbsolutePath());
            values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                return null;
            }
            OutputStream out = resolver.openOutputStream(uri);
            if (out == null) {
                return null;
            }
            try {
                out.write(data);
            } finally {
                out.close();
            }
            return uri;
        } catch (Exception e) {
            Log.e(TAG, "MediaStore save failed: " + e.getMessage());
            return null;
        }
    }

    private static String queryDataPath(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{MediaStore.Images.Media.DATA}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private static void scan(Context context, File file, String mime) {
        MediaScannerConnection.scanFile(
                context.getApplicationContext(),
                new String[]{file.getAbsolutePath()},
                new String[]{mime},
                null);
    }

    static List<String> listImages() {
        List<String> result = new ArrayList<>();
        Context context = App.instance();

        // 公共目录的文件通过媒体库查询，避开普通进程无直接读权限的问题。
        Cursor cursor = null;
        try {
            String selection = "(" + MediaStore.Images.Media.DISPLAY_NAME + " LIKE ? OR "
                    + MediaStore.Images.Media.DATA + " LIKE ?)";
            cursor = context.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media.DATA},
                    selection,
                    new String[]{PREFIX + "%", "%/" + SUBDIR + "/%"},
                    null);
            if (cursor != null) {
                int column = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                while (cursor.moveToNext()) {
                    if (column < 0) {
                        continue;
                    }
                    String path = cursor.getString(column);
                    if (path != null && !result.contains(path) && new File(path).exists()) {
                        result.add(path);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "MediaStore query failed: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        File fallback = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (fallback != null) {
            addImages(result, fallback, false);
        }
        result.sort(Comparator.comparingLong((String p) -> new File(p).lastModified()).reversed());
        return result;
    }

    private static void addImages(List<String> out, File dir, boolean requirePrefix) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            String name = f.getName();
            if (f.isFile() && isImageName(name) && (!requirePrefix || name.startsWith(PREFIX))
                    && !out.contains(f.getAbsolutePath())) {
                out.add(f.getAbsolutePath());
            }
        }
    }

    static boolean isImageName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.US);
        for (String ext : IMAGE_EXT) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static File uniqueFile(File dir, String name) {
        File target = new File(dir, name);
        int n = 2;
        while (target.exists()) {
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            target = new File(dir, base + "_" + n + ext);
            n++;
        }
        return target;
    }

    private static String safeBaseName(String originalName) {
        if (originalName == null || originalName.isEmpty()) {
            return "image";
        }
        String base = originalName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (base.isEmpty()) {
            return "image";
        }
        if (base.length() > 40) {
            base = base.substring(base.length() - 40);
        }
        return base;
    }

    private static String extensionOf(String name, String contentType) {
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && dot < name.length() - 1) {
                String ext = name.substring(dot).toLowerCase(Locale.US);
                if (ext.length() <= 8 && ext.matches("\\.[a-z0-9]+")) {
                    return ext;
                }
            }
        }
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.US);
            if (ct.contains("png")) return ".png";
            if (ct.contains("gif")) return ".gif";
            if (ct.contains("webp")) return ".webp";
            if (ct.contains("bmp")) return ".bmp";
        }
        return ".jpg";
    }
}
