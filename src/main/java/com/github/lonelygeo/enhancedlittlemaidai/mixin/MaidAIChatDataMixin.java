package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.lonelygeo.enhancedlittlemaidai.util.ReasoningContentStore;
import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatData;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.ToolCall;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.CappedQueue;
import com.google.common.collect.Lists;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * reasoningContent + MindPalace NBT 持久化 + 重载 addAssistantHistory。
 */
@Mixin(value = MaidAIChatData.class, remap = false)
public abstract class MaidAIChatDataMixin {
    private static final String MAID_HISTORY_REASONING_TAG = "MaidHistoryReasoningContent";

    @Shadow
    public abstract EntityMaid getMaid();

    @Shadow
    public abstract CappedQueue<LLMMessage> getHistory();

    @Shadow
    protected abstract void onHistoryUpdated();

    @Unique
    public void addAssistantHistory(String message, @Nullable String reasoningContent) {
        LLMMessage llmMsg = LLMMessage.assistantChat(getMaid(), message);
        ReasoningContentStore.put(llmMsg, reasoningContent);
        getHistory().add(llmMsg);
        onHistoryUpdated();
    }

    @Unique
    public void addAssistantHistory(String message, List<ToolCall> toolCalls, @Nullable String reasoningContent) {
        LLMMessage llmMsg = LLMMessage.assistantChat(getMaid(), message, toolCalls);
        ReasoningContentStore.put(llmMsg, reasoningContent);
        getHistory().add(llmMsg);
        onHistoryUpdated();
    }

    @Inject(method = "readFromTag", at = @At("TAIL"), remap = false)
    private void enhanced$readReasoningContent(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (tag.contains(MAID_HISTORY_REASONING_TAG, Tag.TAG_LIST)) {
            ListTag reasoningList = tag.getList(MAID_HISTORY_REASONING_TAG, Tag.TAG_STRING);
            if (!reasoningList.isEmpty()) {
                List<LLMMessage> messages = Lists.newArrayList(getHistory().getDeque());
                int count = Math.min(reasoningList.size(), messages.size());
                for (int i = 0; i < count; i++) {
                    String rc = reasoningList.getString(i);
                    if (StringUtils.isNotBlank(rc)) {
                        ReasoningContentStore.put(messages.get(i), rc);
                    }
                }
            }
        }

        EntityMaid maid = getMaid();
        if (maid != null) {
            MindPalace palace = MindPalace.getOrCreate(maid.getUUID());
            palace.readFromTag(cir.getReturnValue());
            if (EnhancedConfig.debugLog() && palace.size() > 0) {
                EnhancedLittleMaidAI.LOGGER.debug(
                        "EnhancedLittleMaidAI: Loaded {} MindPalace memories for maid {}",
                        palace.size(), maid.getUUID());
            }
        }
    }

    @Inject(method = "writeToTag", at = @At("TAIL"), remap = false)
    private void enhanced$writeReasoningContent(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        List<LLMMessage> messages = Lists.newArrayList(getHistory().getDeque());
        if (!messages.isEmpty()) {
            ListTag reasoningList = new ListTag();
            for (LLMMessage msg : messages) {
                String rc = ReasoningContentStore.get(msg);
                reasoningList.add(StringTag.valueOf(rc != null ? rc : ""));
            }
            tag.put(MAID_HISTORY_REASONING_TAG, reasoningList);
        }

        ReasoningContentStore.pruneStaleEntries(new java.util.HashSet<>(messages));

        EntityMaid maid = getMaid();
        if (maid != null) {
            MindPalace palace = MindPalace.get(maid.getUUID());
            if (palace != null && palace.size() > 0) {
                palace.writeToTag(cir.getReturnValue());
                if (EnhancedConfig.debugLog()) {
                    EnhancedLittleMaidAI.LOGGER.debug(
                            "EnhancedLittleMaidAI: Saved {} MindPalace memories for maid {}",
                            palace.size(), maid.getUUID());
                }
            }
        }
    }
}
