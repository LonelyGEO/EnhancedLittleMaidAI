package com.github.lonelygeo.enhancedlittlemaidai.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 当记忆数超过 80 条时，合并相似旧记忆。
 * 简化版：按淘汰分数排序，直接丢弃最低分的记忆。
 */
public final class MemoryCompressor {
    private MemoryCompressor() {}

    /**
     * 精简记忆至目标数量。
     * 返回被移除的记忆列表。
     */
    public static List<MemoryItem> compress(MemoryStore store, int targetSize, long currentGameTime) {
        if (store.size() <= targetSize) return List.of();

        List<MemoryItem> all = new ArrayList<>(store.toList());
        all.sort(Comparator.comparingDouble(m ->
                -m.evictionScore(currentGameTime, 20L * 60 * 20 * 30)));

        List<MemoryItem> removed = new ArrayList<>(all.subList(targetSize, all.size()));

        // 重建 Store
        MemoryStore newStore = new MemoryStore();
        for (MemoryItem item : all.subList(0, targetSize)) {
            newStore.restore(item);
        }

        return removed;
    }
}
