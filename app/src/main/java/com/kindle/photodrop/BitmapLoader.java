package com.kindle.photodrop;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

final class BitmapLoader {

    private BitmapLoader() {
    }

    static Bitmap decode(String path, int maxWidth, int maxHeight) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (bounds.outWidth / sample > maxWidth || bounds.outHeight / sample > maxHeight) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, options);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }
}
