package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.lonelygeo.enhancedlittlemaidai.compat.MiningChatCallback;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryExtractionCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * LLM 响应缓存。
 * 对相同 system prompt + 用户消息的请求缓存 LLM 回复，减少 API 调用。
 * 仅缓存主动聊天和常规对话，排除工具调用、记忆提取、采矿对话。
 */
public final class LLMResponseCache {
    private static final int MAX_ENTRIES = 20;
    private static final String DIGEST_ALGO = "SHA-256";
    private static final SimpleResponseCache CACHE = new SimpleResponseCache(MAX_ENTRIES);

    private LLMResponseCache() {
    }

    /**
     * 计算缓存键：SHA-256(所有 SYSTEM 消息 + 最后一条 USER 消息)
     */
    public static String computeKey(LLMCallback callback) {
        try {
            List<LLMMessage> messages = callback.getMessages();
            MessageDigest md = MessageDigest.getInstance(DIGEST_ALGO);

            String lastUser = "";
            for (int i = messages.size() - 1; i >= 0; i--) {
                LLMMessage msg = messages.get(i);
                if (msg.role() == com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role.USER) {
                    lastUser = msg.message();
                    break;
                }
            }

            for (LLMMessage msg : messages) {
                if (msg.role() == com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role.SYSTEM) {
                    md.update((byte) 0);
                    md.update(msg.message().getBytes(StandardCharsets.UTF_8));
                }
            }
            md.update((byte) 0);
            md.update(lastUser.getBytes(StandardCharsets.UTF_8));

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取缓存，检查 TTL。
     */
    public static ResponseChat get(String key, long ttlMs) {
        String text = CACHE.get(key, ttlMs);
        return text != null ? new ResponseChat(text) : null;
    }

    /** 存入缓存 */
    public static void put(String key, String chatText) {
        CACHE.put(key, chatText);
    }

    /**
     * 是否应该缓存此 callback 类型。
     */
    public static boolean shouldCache(LLMCallback callback) {
        if (callback instanceof MemoryExtractionCallback) return false;
        if (callback instanceof MiningChatCallback) return false;
        return !callback.needAddTools;
    }

    /** 获取 TTL：主动聊天 60s，常规 30s */
    public static long getTtlMs(LLMCallback callback) {
        return callback instanceof ProactiveChatCallback ? 60_000L : 30_000L;
    }
}
