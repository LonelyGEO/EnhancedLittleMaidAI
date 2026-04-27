package com.github.lonelygeo.enhancedlittlemaidai.memory;

import com.github.lonelygeo.enhancedlittlemaidai.util.bm25.Bm25Index;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * 单个女仆的记忆存储。
 * 管理记忆列表 + BM25 索引，提供增删检 + 去重 + 淘汰。
 */
public class MemoryStore {
    private static final int MAX_MEMORIES = 100;
    private static final int COMPRESS_TRIGGER = 80;
    private static final double DEDUP_SCORE_THRESHOLD = 0.85;
    private static final long MAX_AGE_TICKS = 20L * 60 * 20 * 30;

    private final List<MemoryItem> memories = new ArrayList<>();
    private final Bm25Index index = new Bm25Index();

    /**
     * 添加记忆。返回 true 表示成功添加，false 表示重复被跳过。
     */
    public boolean add(MemoryItem memory) {
        List<Bm25Index.ScoredDoc> similar = index.search(memory.content(), 1);
        if (!similar.isEmpty() && similar.getFirst().score() > DEDUP_SCORE_THRESHOLD) {
            return false;
        }

        memories.add(memory);
        index.add(memory.id().toString(), memory.content());

        if (TouhouLittleMaid.DEBUG) {
            TouhouLittleMaid.LOGGER.debug("EnhancedLittleMaidAI: Memory added, total={}", memories.size());
        }

        if (memories.size() > MAX_MEMORIES) {
            evict(0);
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
        if (memories.size() <= MAX_MEMORIES) return;

        int before = memories.size();
        memories.sort(Comparator.comparingDouble(m ->
                -m.evictionScore(currentGameTime, MAX_AGE_TICKS)));
        while (memories.size() > MAX_MEMORIES) {
            MemoryItem removed = memories.removeLast();
            index.remove(removed.id().toString());
        }
        if (TouhouLittleMaid.DEBUG) {
            TouhouLittleMaid.LOGGER.debug("EnhancedLittleMaidAI: Memory evicted {} items, remaining={}",
                    before - memories.size(), memories.size());
        }
    }

    public boolean needsCompression() {
        return memories.size() >= COMPRESS_TRIGGER;
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
}
