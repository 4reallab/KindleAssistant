package com.kindle.photodrop;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 已接收图片的宫格列表。 */
public class GalleryActivity extends Activity {

    private ImageAdapter adapter;
    private List<String> paths = new ArrayList<>();

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UploadService.BROADCAST_IMAGE.equals(intent.getAction())) {
                reload();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        GridView grid = findViewById(R.id.gridView);
        adapter = new ImageAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            Intent intent = new Intent(this, ViewerActivity.class);
            intent.putStringArrayListExtra("paths", new ArrayList<>(paths));
            intent.putExtra("index", position);
            startActivity(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(receiver, new IntentFilter(UploadService.BROADCAST_IMAGE));
        reload();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(receiver);
        super.onStop();
    }

    private void reload() {
        paths = ImageStore.listImages();
        TextView count = findViewById(R.id.countText);
        TextView empty = findViewById(R.id.emptyText);
        count.setText("已收到 " + paths.size() + " 张图片");
        empty.setVisibility(paths.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private final class ImageAdapter extends BaseAdapter {

        private final LruCache<String, Bitmap> cache = new LruCache<>(36);

        @Override
        public int getCount() {
            return paths.size();
        }

        @Override
        public Object getItem(int position) {
            return paths.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView image;
            if (convertView instanceof ImageView) {
                image = (ImageView) convertView;
            } else {
                image = (ImageView) getLayoutInflater().inflate(R.layout.item_image, parent, false);
            }
            final String path = paths.get(position);
            image.setTag(path);
            Bitmap cached = cache.get(path);
            if (cached != null) {
                image.setImageBitmap(cached);
            } else {
                image.setImageResource(android.R.color.darker_gray);
                loadThumb(path, image);
            }
            return image;
        }

        private void loadThumb(final String path, final ImageView target) {
            new AsyncTask<Void, Void, Bitmap>() {
                @Override
                protected Bitmap doInBackground(Void... ignored) {
                    return BitmapLoader.decode(path, 300, 300);
                }

                @Override
                protected void onPostExecute(Bitmap bitmap) {
                    if (bitmap == null) {
                        return;
                    }
                    cache.put(path, bitmap);
                    if (path.equals(target.getTag())) {
                        target.setImageBitmap(bitmap);
                    }
                }
            }.execute();
        }
    }
}
