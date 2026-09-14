package com.qianwen.demo.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 极简图片加载器：内存缓存 + 后台下载解码 + 按目标尺寸采样 + 回收复用校验。
 * 缩略图场景足够用，因此不额外引入图片库依赖。
 */
public final class ImageLoader {
    private static final int MAX_CACHE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_CONCURRENT = 3;

    private static final ImageLoader INSTANCE = new ImageLoader();

    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memoryCache = new LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private ImageLoader() {
    }

    public static ImageLoader get() {
        return INSTANCE;
    }

    /**
     * 加载网络图片到 ImageView。
     *
     * @param targetSizePx 期望的短边像素（按采样解码，避免把原图整张读进内存）
     */
    public void load(String url, ImageView view, int targetSizePx) {
        // 用 tag 记录本次绑定的 url，异步回来时校验，避免 RecyclerView 复用串图。
        view.setTag(url);
        view.setImageDrawable(null);
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        Bitmap cached = memoryCache.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }

        final String requestUrl = url;
        final int size = Math.max(1, targetSizePx);
        executor.submit(() -> {
            Bitmap bitmap;
            try {
                bitmap = download(requestUrl, size);
            } catch (IOException error) {
                return;
            }
            if (bitmap == null) {
                return;
            }
            memoryCache.put(requestUrl, bitmap);
            mainHandler.post(() -> {
                if (requestUrl.equals(view.getTag())) {
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    private Bitmap download(String url, int targetSizePx) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            byte[] bytes = response.body().bytes();

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetSizePx);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        }
    }

    private static int sampleSize(int width, int height, int targetSizePx) {
        int sample = 1;
        int shortest = Math.min(width, height);
        while (shortest / (sample * 2) >= targetSizePx) {
            sample *= 2;
        }
        return sample;
    }
}
