package com.github.lonelygeo.enhancedlittlemaidai.memory;

import com.github.lonelygeo.enhancedlittlemaidai.util.bm25.Bm25Index;
import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * 单个女仆的记忆存储。
 * 管理记忆列表 + BM25 索引，提供增删检 + 去重 + 淘汰。
 */
public class MemoryStore {

    private final List<MemoryItem> memories = new ArrayList<>();
    private final Bm25Index index = new Bm25Index();

    /** 添加记忆，传入当前游戏时间用于淘汰评分。返回 true 表示成功添加，false 表示重复。 */
    public boolean add(MemoryItem memory, long gameTime) {
        List<Bm25Index.ScoredDoc> similar = index.search(memory.content(), 1);
        if (!similar.isEmpty() && similar.getFirst().score() > EnhancedConfig.DEDUP_SCORE_THRESHOLD.get()) {
            return false;
        }

        memories.add(memory);
        index.add(memory.id().toString(), memory.content());

        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: Memory added, total={}", memories.size());
        }

        if (memories.size() > EnhancedConfig.MAX_MEMORIES.get()) {
            evict(gameTime);
        }

        return true;
    }

    /** 从备份恢复记忆（跳过去重，因为来自 NBT） */
    public void restore(MemoryItem memory) {
        memories.add(memory);
        index.add(memory.id().toString(), memory.content());
    }

    /** BM25 语义检索 Top-K */
    public List<MemoryItem> retrieve(String query, int topK) {
        List<Bm25Index.ScoredDoc> results = index.search(query, topK);
        List<MemoryItem> result = new ArrayList<>();
        for (Bm25Index.ScoredDoc doc : results) {
            for (int i = 0; i < memories.size(); i++) {
                MemoryItem m = memories.get(i);
                if (m.id().toString().equals(doc.docId())) {
                    memories.set(i, m.incrementAccess());
                    result.add(m);
                    break;
                }
            }
        }
        return result;
    }

    /** 空间召回：在指定位置半径范围内的记忆 */
    public List<MemoryItem> retrieveByLocation(BlockPos pos, int radius) {
        int radiusSq = radius * radius;
        return memories.stream()
                .filter(m -> m.location().isPresent())
                .filter(m -> m.location().get().distSqr(pos) <= radiusSq)
                .sorted(Comparator.comparingInt(MemoryItem::importance).reversed())
                .limit(5)
                .toList();
    }

    /** 淘汰 lowest-score 记忆 */
    public void evict(long currentGameTime) {
        if (memories.size() <= EnhancedConfig.MAX_MEMORIES.get()) return;

        int before = memories.size();
        memories.sort(Comparator.comparingDouble(m ->
                -m.evictionScore(currentGameTime, EnhancedConfig.MAX_AGE_TICKS.get())));
        while (memories.size() > EnhancedConfig.MAX_MEMORIES.get()) {
            MemoryItem removed = memories.removeLast();
            index.remove(removed.id().toString());
        }
        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: Memory evicted {} items, remaining={}",
                    before - memories.size(), memories.size());
        }
    }

    public boolean needsCompression() {
        return memories.size() >= EnhancedConfig.COMPRESS_TRIGGER.get();
    }

    public List<MemoryItem> toList() {
        return Collections.unmodifiableList(memories);
    }

    public static MemoryStore fromList(List<MemoryItem> items) {
        MemoryStore store = new MemoryStore();
        for (MemoryItem item : items) {
            store.restore(item);
        }
        return store;
    }

    public int size() {
        return memories.size();
    }

    /** 删除最旧的 N 条记忆（按 gameTime 升序）。用于压缩后替换。 */
    public void removeOldest(int count) {
        if (memories.size() <= count) {
            for (MemoryItem m : List.copyOf(memories)) {
                index.remove(m.id().toString());
            }
            memories.clear();
            return;
        }
        List<MemoryItem> sorted = new ArrayList<>(memories);
        sorted.sort(Comparator.comparingLong(MemoryItem::gameTime));
        for (int i = 0; i < count; i++) {
            MemoryItem removed = sorted.get(i);
            memories.remove(removed);
            index.remove(removed.id().toString());
        }
    }
}
