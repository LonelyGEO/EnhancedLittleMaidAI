package com.github.lonelygeo.enhancedlittlemaidai.memory;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 思维宫殿 —— 每个女仆实例对应一个 MindPalace。
 * 存储于全局 Map<MaidUUID, MindPalace>，模式与 ReasoningContentStore 一致。
 */
public class MindPalace {
    private static final Map<UUID, MindPalace> PALACES =
            Collections.synchronizedMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, MindPalace> eldest) {
                    return size() > 1000;
                }
            });

    private static final int SPATIAL_RECALL_RADIUS = 16;
    private static final int SEMANTIC_RETRIEVE_TOP_K = 3;

    private final UUID maidUuid;
    private final MemoryStore store = new MemoryStore();
    private int chatRoundCounter = 0;
    private long lastExtractionGameTime = 0;

    private MindPalace(UUID maidUuid) {
        this.maidUuid = maidUuid;
    }

    public static MindPalace getOrCreate(UUID maidUuid) {
        return PALACES.computeIfAbsent(maidUuid, MindPalace::new);
    }

    public static MindPalace get(UUID maidUuid) {
        return PALACES.get(maidUuid);
    }

    public static void remove(UUID maidUuid) {
        PALACES.remove(maidUuid);
    }

    /**
     * 构建 `<memory>` XML 片段，用于注入到 LLM 消息列表。
     * 包含：语义检索 Top-K + 空间召回（如果女仆在记忆位置附近）。
     */
    public String buildMemoryContext(String userMessage, BlockPos maidPos) {
        List<MemoryItem> semanticResults = store.retrieve(userMessage, SEMANTIC_RETRIEVE_TOP_K);
        List<MemoryItem> spatialResults = store.retrieveByLocation(maidPos, SPATIAL_RECALL_RADIUS);

        Set<MemoryItem> merged = new LinkedHashSet<>(semanticResults);
        merged.addAll(spatialResults);

        if (merged.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<memory>\n");
        for (MemoryItem m : merged) {
            sb.append("  <item");
            if (m.location().isPresent()) {
                BlockPos loc = m.location().get();
                sb.append(" location=\"").append(loc.getX())
                        .append(",").append(loc.getY())
                        .append(",").append(loc.getZ()).append("\"");
            }
            sb.append(" category=\"").append(m.category().name().toLowerCase()).append("\"");
            sb.append(">").append(m.content()).append("</item>\n");
        }
        sb.append("</memory>");
        return sb.toString();
    }

    public void incrementRoundCounter() {
        chatRoundCounter++;
    }

    public boolean shouldExtractMemories(long currentGameTime) {
        return chatRoundCounter % 5 == 0 && chatRoundCounter > 0
                && (currentGameTime - lastExtractionGameTime) > 100;
    }

    public void markExtractionDone(long currentGameTime) {
        lastExtractionGameTime = currentGameTime;
    }

    public void addMemories(List<MemoryItem> items, long currentGameTime) {
        for (MemoryItem item : items) {
            store.add(item, currentGameTime);
        }
    }

    /** 添加单条记忆，使用记忆自带的时间戳。 */
    public void addMemory(MemoryItem item) {
        store.add(item, item.gameTime());
    }

    public void readFromTag(CompoundTag tag) {
        if (!tag.contains("MindPalaceMemories", Tag.TAG_LIST)) return;
        ListTag list = tag.getList("MindPalaceMemories", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            MemoryItem item = MemoryItem.fromTag(list.getCompound(i));
            store.restore(item);
        }
    }

    public void writeToTag(CompoundTag tag) {
        ListTag list = new ListTag();
        for (MemoryItem m : store.toList()) {
            list.add(m.toTag());
        }
        tag.put("MindPalaceMemories", list);
    }

    public MemoryStore getStore() {
        return store;
    }

    public int size() {
        return store.size();
    }

    public boolean needsCompression() {
        return store.needsCompression();
    }

    // ==================== LLM 记忆压缩 ====================

    /** 异步触发 LLM 记忆压缩。由 LLMCallbackMixin 调用。 */
    public void triggerCompression(EntityMaid maid, LLMClient llmClient) {
        List<String> oldTexts = MemoryCompressor.getOldestMemoryTexts(store);
        if (oldTexts.isEmpty()) return;

        String prompt = MemoryCompressor.buildCompressPrompt(oldTexts);
        LLMMessage sysMsg = LLMMessage.systemChat(maid, prompt);
        List<LLMMessage> msgs = List.of(sysMsg);

        CompletableFuture<List<MemoryItem>> future = new CompletableFuture<>();
        MemoryExtractionCallback cb = new MemoryExtractionCallback(
                null, msgs, future);
        llmClient.chat(cb);

        future.whenComplete((summaries, ex) -> {
            if (ex == null && summaries != null && !summaries.isEmpty()) {
                applyCompressionResult(summaries);
                if (EnhancedLittleMaidAI.DEBUG_LOG) {
                    EnhancedLittleMaidAI.LOGGER.info(
                            "EnhancedLittleMaidAI: Memory compression completed for maid {}: {} summaries",
                            maidUuid, summaries.size());
                }
            } else if (ex != null) {
                EnhancedLittleMaidAI.LOGGER.warn(
                        "EnhancedLittleMaidAI: Memory compression failed for maid {}", maidUuid, ex);
            }
        });
    }

    private void applyCompressionResult(List<MemoryItem> summaries) {
        store.removeOldest(MemoryCompressor.COMPRESS_BATCH_SIZE);
        for (MemoryItem item : summaries) {
            store.restore(item);
        }
    }
}
