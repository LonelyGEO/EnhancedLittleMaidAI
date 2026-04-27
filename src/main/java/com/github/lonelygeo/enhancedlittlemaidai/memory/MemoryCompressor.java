package com.github.lonelygeo.enhancedlittlemaidai.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * LLM 版记忆压缩器。
 * 触发条件：store.size() >= COMPRESS_TRIGGER (80)
 * 策略：取最旧的 20 条记忆发给 LLM，合并为最多 5 条摘要，替换原记忆。
 */
public final class MemoryCompressor {
    static final int COMPRESS_TRIGGER = 80;
    static final int COMPRESS_BATCH_SIZE = 20;
    static final int TARGET_SUMMARIES = 5;

    private MemoryCompressor() {}

    public static String buildCompressPrompt(List<String> memoryTexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a memory summarizer. Merge similar old memories into concise summaries.
                Output one line per summary in this exact format:
                [CATEGORY] content | importance:N

                Rules:
                - Combine memories of the same CATEGORY that share a topic
                - Keep ALL entity names, coordinates, and specific facts
                - Shorten descriptions, never lose facts
                - Output ONLY the summary lines, nothing else
                - Output at most %d summaries

                Memories to compress:
                """.formatted(TARGET_SUMMARIES));

        for (String text : memoryTexts) {
            sb.append(text).append("\n");
        }
        return sb.toString();
    }

    public static boolean needsCompression(MemoryStore store) {
        return store.size() >= COMPRESS_TRIGGER;
    }

    public static List<String> getOldestMemoryTexts(MemoryStore store) {
        List<MemoryItem> items = new ArrayList<>(store.toList());
        items.sort(Comparator.comparingLong(MemoryItem::gameTime));
        int count = Math.min(COMPRESS_BATCH_SIZE, items.size());
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MemoryItem m = items.get(i);
            texts.add(String.format("[%s] %s | importance:%d",
                    m.category().name(), m.content(), m.importance()));
        }
        return texts;
    }
}
