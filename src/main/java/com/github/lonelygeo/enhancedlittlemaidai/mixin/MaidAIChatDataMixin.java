package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatData;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.CappedQueue;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.LinkedBlockingDeque;

/**
 * Phase 1: writeToTag 时去除 reasoningContent，防止魂符 NBT 达 2MB 上限。
 * MindPalace 持久化已移至 EntityMaidMixin，避免参与网络包同步。
 */
@Mixin(value = MaidAIChatData.class, remap = false)
public abstract class MaidAIChatDataMixin {

    @Shadow
    public abstract EntityMaid getMaid();

    @Shadow
    public abstract CappedQueue<LLMMessage> getHistory();

    @Unique
    private static final ThreadLocal<Boolean> enhanced$inSave = ThreadLocal.withInitial(() -> false);

    @Inject(method = "writeToTag", at = @At("HEAD"), remap = false)
    private void enhanced$beforeSave(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        enhanced$inSave.set(true);
    }

    @Inject(method = "writeToTag", at = @At("TAIL"), remap = false)
    private void enhanced$afterSave(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        enhanced$inSave.remove();
    }

    @Redirect(
            method = "writeToTag",
            at = @At(value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/util/CappedQueue;"
                             + "getDeque()Ljava/util/concurrent/LinkedBlockingDeque;"),
            remap = false,
            require = 0
    )
    private LinkedBlockingDeque<LLMMessage> enhanced$stripForSave(CappedQueue<LLMMessage> history) {
        if (!enhanced$inSave.get()) {
            return history.getDeque();
        }

        LinkedBlockingDeque<LLMMessage> stripped = new LinkedBlockingDeque<>();
        for (LLMMessage msg : history.getDeque()) {
            if (msg.reasoningContent() != null && !msg.reasoningContent().isEmpty()) {
                stripped.add(new LLMMessage(
                        msg.role(), msg.message(), msg.gameTime(),
                        msg.toolCalls(), msg.toolCallId(), null));
            } else {
                stripped.add(msg);
            }
        }
        return stripped;
    }
}
