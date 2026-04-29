package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatData;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.CappedQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Mixin 占位。MindPalace 持久化已移至 EntityMaidMixin 的 addAdditionalSaveData / readAdditionalSaveData，
 * 避免参与 SyncMaidAIDataPacket 网络同步导致 NBT 超 2MB 上限。
 */
@Mixin(value = MaidAIChatData.class, remap = false)
public abstract class MaidAIChatDataMixin {

    @Shadow
    public abstract EntityMaid getMaid();

    @Shadow
    public abstract CappedQueue<LLMMessage> getHistory();
}
