package com.github.lonelygeo.enhancedlittlemaidai.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用的 LRU 字符串缓存，不依赖 Minecraft/NeoForge。
 * LLMResponseCache 委托此类处理 put/get/TTL/eviction。
 */
final class SimpleResponseCache {
    private final int maxEntries;
    private final Map<String, TimedEntry> cache;

    SimpleResponseCache(int maxEntries) {
        this.maxEntries = maxEntries;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TimedEntry> eldest) {
                return size() > maxEntries;
            }
        };
    }

    String get(String key, long ttlMs) {
        TimedEntry entry = cache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.createdAt > ttlMs) {
            cache.remove(key);
            return null;
        }
        return entry.text;
    }

    void put(String key, String text) {
        if (key == null || text == null) return;
        cache.put(key, new TimedEntry(System.currentTimeMillis(), text));
    }

    int size() {
        return cache.size();
    }

    private record TimedEntry(long createdAt, String text) {}
}
