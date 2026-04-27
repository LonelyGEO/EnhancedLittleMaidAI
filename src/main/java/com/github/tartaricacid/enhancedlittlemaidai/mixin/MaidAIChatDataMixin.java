package com.github.tartaricacid.enhancedlittlemaidai.mixin;

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
 * 为 MaidAIChatData 添加：
 * <ul>
 *   <li>支持 reasoningContent 的 addAssistantHistory 重载方法</li>
 *   <li>NBT 持久化：将 reasoningContent 存入独立的 MaidHistoryReasoningContent 列表</li>
 * </ul>
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

    /**
     * 添加 assistant 历史记录，附带 reasoningContent。
     */
    @Unique
    public void addAssistantHistory(String message, @Nullable String reasoningContent) {
        LLMMessage llmMsg = LLMMessage.assistantChat(getMaid(), message);
        ((LLMMessageMixin) (Object) llmMsg).enhancedSetReasoningContent(reasoningContent);
        getHistory().add(llmMsg);
        onHistoryUpdated();
    }

    /**
     * 添加 assistant 历史记录（含 toolCalls 和 reasoningContent）。
     */
    @Unique
    public void addAssistantHistory(String message, List<ToolCall> toolCalls, @Nullable String reasoningContent) {
        LLMMessage llmMsg = LLMMessage.assistantChat(getMaid(), message, toolCalls);
        ((LLMMessageMixin) (Object) llmMsg).enhancedSetReasoningContent(reasoningContent);
        getHistory().add(llmMsg);
        onHistoryUpdated();
    }

    /**
     * 读档后，将 reasoningContent 回填到历史消息中。
     */
    @Inject(method = "readFromTag", at = @At("TAIL"), remap = false)
    private void enhanced$readReasoningContent(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (!tag.contains(MAID_HISTORY_REASONING_TAG, Tag.TAG_LIST)) {
            return;
        }
        ListTag reasoningList = tag.getList(MAID_HISTORY_REASONING_TAG, Tag.TAG_STRING);
        if (reasoningList.isEmpty()) {
            return;
        }

        List<LLMMessage> messages = Lists.newArrayList(getHistory().getDeque());
        int count = Math.min(reasoningList.size(), messages.size());
        for (int i = 0; i < count; i++) {
            String rc = reasoningList.getString(i);
            if (StringUtils.isNotBlank(rc)) {
                ((LLMMessageMixin) (Object) messages.get(i)).enhancedSetReasoningContent(rc);
            }
        }
    }

    /**
     * 存盘前，将 reasoningContent 序列化到独立 tag 中。
     */
    @Inject(method = "writeToTag", at = @At("TAIL"), remap = false)
    private void enhanced$writeReasoningContent(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        List<LLMMessage> messages = Lists.newArrayList(getHistory().getDeque());
        if (messages.isEmpty()) {
            return;
        }

        ListTag reasoningList = new ListTag();
        for (LLMMessage msg : messages) {
            String rc = ((LLMMessageMixin) (Object) msg).reasoningContent();
            reasoningList.add(StringTag.valueOf(rc != null ? rc : ""));
        }
        tag.put(MAID_HISTORY_REASONING_TAG, reasoningList);
    }
}
