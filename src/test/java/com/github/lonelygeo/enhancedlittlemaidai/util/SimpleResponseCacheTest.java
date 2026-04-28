package com.github.lonelygeo.enhancedlittlemaidai.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * SimpleResponseCache 单元测试 — 核心 LRU 缓存 put/get/TTL/eviction 逻辑。
 * 纯字符串操作，不依赖 Minecraft/NeoForge 运行时。
 */
public class SimpleResponseCacheTest {

    private final SimpleResponseCache cache = new SimpleResponseCache(10);

    @Test
    public void testPutAndGet() {
        cache.put("k1", "你好");
        assertEquals("你好", cache.get("k1", 60000));
    }

    @Test
    public void testExpiredTtl() throws Exception {
        cache.put("k2", "过期");
        Thread.sleep(2);
        assertNull(cache.get("k2", 1));
    }

    @Test
    public void testValidTtl() {
        cache.put("k3", "有效");
        assertNotNull(cache.get("k3", 60000));
    }

    @Test
    public void testNonExistentKey() {
        assertNull(cache.get("nonexistent", 60000));
    }

    @Test
    public void testNullKey() {
        cache.put(null, "val");
        assertNull(cache.get(null, 60000));
    }

    @Test
    public void testNullValue() {
        cache.put("k4", null);
        assertNull(cache.get("k4", 60000));
    }

    @Test
    public void testMultipleEntries() {
        for (int i = 0; i < 5; i++) {
            cache.put("key-" + i, "val-" + i);
        }
        assertEquals(5, cache.size());
        for (int i = 0; i < 5; i++) {
            assertNotNull(cache.get("key-" + i, 60000));
        }
    }

    @Test
    public void testOverwriteKey() {
        cache.put("dup", "旧");
        cache.put("dup", "新");
        assertEquals("新", cache.get("dup", 60000));
    }

    @Test
    public void testLruEviction() {
        SimpleResponseCache small = new SimpleResponseCache(3);
        small.put("a", "1");
        small.put("b", "2");
        small.put("c", "3");
        assertEquals(3, small.size());
        // touch a
        small.get("a", 60000);
        // add d → evict b (LRU)
        small.put("d", "4");
        assertEquals(3, small.size());
        assertNotNull(small.get("a", 60000));
        assertNull(small.get("b", 60000)); // evicted
        assertNotNull(small.get("c", 60000));
        assertNotNull(small.get("d", 60000));
    }
}
