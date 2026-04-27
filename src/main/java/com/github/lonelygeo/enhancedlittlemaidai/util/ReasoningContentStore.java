package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 跨 Mixin 共享的 reasoningContent 存储器。
 */
public final class ReasoningContentStore {
    private static final Map<LLMMessage, String> STORE = Collections.synchronizedMap(new IdentityHashMap<>());

    public static void put(LLMMessage msg, @Nullable String content) {
        if (content != null && !content.isEmpty()) {
            STORE.put(msg, content);
        }
    }

    @Nullable
    public static String get(LLMMessage msg) {
        return STORE.get(msg);
    }

    /**
     * 清理所有不在给定集合中的陈旧消息引用。
     */
    public static void pruneStaleEntries(Set<LLMMessage> activeMessages) {
        synchronized (STORE) {
            STORE.keySet().removeIf(msg -> !activeMessages.contains(msg));
        }
    }

    /**
     * 通过反射获取 Message 对象的 reasoningContent 字段。
     */
    @Nullable
    public static String getFromMessage(Object message) {
        try {
            java.lang.reflect.Field field = message.getClass().getDeclaredField("reasoningContent");
            field.setAccessible(true);
            return (String) field.get(message);
        } catch (Exception e) {
            return null;
        }
    }
}
