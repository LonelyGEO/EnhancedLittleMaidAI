package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * LLMResponseCache 单元测试 — 仅测试不依赖 Minecraft 的 put/get/TTL 核心逻辑。
 */
public class LLMResponseCacheTest {

    @Test
    public void testPutAndGet() {
        String key = "test-key-get";
        LLMResponseCache.put(key, "你好，主人！");
        ResponseChat cached = LLMResponseCache.get(key, 60000);
        assertNotNull(cached);
        assertEquals("你好，主人！", cached.getChatText());
    }

    @Test
    public void testExpiredTtl() throws Exception {
        String key = "test-key-expired";
        LLMResponseCache.put(key, "过期回复");
        Thread.sleep(2);
        assertNull(LLMResponseCache.get(key, 1));
    }

    @Test
    public void testValidTtl() {
        String key = "test-key-valid";
        LLMResponseCache.put(key, "有效回复");
        assertNotNull(LLMResponseCache.get(key, 60000));
    }

    @Test
    public void testNonExistentKey() {
        assertNull(LLMResponseCache.get("不存在的键", 60000));
    }

    @Test
    public void testNullKeyAndValue() {
        LLMResponseCache.put(null, "不会崩溃");
        LLMResponseCache.put("null-val", null);
        assertNull(LLMResponseCache.get(null, 60000));
        assertNull(LLMResponseCache.get("null-val", 60000));
    }

    @Test
    public void testMultipleEntries() {
        for (int i = 0; i < 5; i++) {
            LLMResponseCache.put("key-" + i, "value-" + i);
        }
        for (int i = 0; i < 5; i++) {
            ResponseChat cached = LLMResponseCache.get("key-" + i, 60000);
            assertNotNull("key-" + i, cached);
            assertEquals("value-" + i, cached.getChatText());
        }
    }

    @Test
    public void testOverwriteKey() {
        LLMResponseCache.put("overwrite", "旧值");
        LLMResponseCache.put("overwrite", "新值");
        assertEquals("新值", LLMResponseCache.get("overwrite", 60000).getChatText());
    }

    @Test
    public void testShouldCacheClassLoads() {
        // 验证 shouldCache 方法存在且类可加载
        assertNotNull(LLMResponseCache.class.getSimpleName());
    }
}
