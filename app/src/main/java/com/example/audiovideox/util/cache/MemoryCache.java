package com.example.audiovideox.util.cache;

import android.graphics.Bitmap;
import android.util.LruCache;

public class MemoryCache implements ImageCache {
    private static LruCache<String, Bitmap> lruCache;

    static {
        lruCache = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 1024 / 4)) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                //得到当前图片大小 单位KB
                return value.getWidth() * value.getHeight() / 1024;
            }
        };
    }

    @Override
    public void put(String name, Bitmap bitmap) {
        lruCache.put(name, bitmap);
    }

    @Override
    public Bitmap get(String name) {
        return lruCache.get(name);
    }
}
