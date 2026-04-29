package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatData;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.CappedQueue;
import com.google.common.collect.Lists;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * MindPalace NBT 持久化。
 * TLM 已内置 reasoningContent 支持（LLMMessage record 字段），此处不再重复管理。
 */
@Mixin(value = MaidAIChatData.class, remap = false)
public abstract class MaidAIChatDataMixin {

    @Shadow
    public abstract EntityMaid getMaid();

    @Shadow
    public abstract CappedQueue<LLMMessage> getHistory();

    @Inject(method = "readFromTag", at = @At("TAIL"), remap = false)
    private void enhanced$loadMindPalace(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
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
    private void enhanced$saveMindPalace(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
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
