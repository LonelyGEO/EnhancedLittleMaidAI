package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryCategory;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryItem;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.lonelygeo.enhancedlittlemaidai.util.ProactiveChatCallback;
import com.github.lonelygeo.enhancedlittlemaidai.util.ProactiveChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 女仆死亡时记录死亡记忆到 MindPalace；移除时清理全局 Map。
 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidMixin {

    /**
     * HEAD: 死亡时在 NBT 保存前写入死亡记忆。
     */
    @Inject(method = "remove", at = @At("HEAD"), remap = false)
    private void enhanced$onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if (reason != Entity.RemovalReason.KILLED) return;

        EntityMaid maid = (EntityMaid) (Object) this;
        MindPalace palace = MindPalace.get(maid.getUUID());
        if (palace == null) return;

        String cause = "未知原因";
        try {
            var ds = maid.getLastDamageSource();
            if (ds != null) {
                var entity = ds.getEntity();
                if (entity != null) {
                    cause = entity.getDisplayName().getString();
                } else {
                    cause = ds.getMsgId();
                }
            }
        } catch (Exception ignored) {
        }

        String heldItem = "空手";
        try {
            var mainHand = maid.getMainHandItem();
            if (mainHand != null && !mainHand.isEmpty()) {
                heldItem = mainHand.getDescriptionId();
            }
        } catch (Exception ignored) {
        }

        String task = "无";
        try {
            var currentTask = maid.getTask();
            if (currentTask != null) {
                task = currentTask.getUid().toString();
            }
        } catch (Exception ignored) {
        }

        String deathContent = String.format("被 %s 击杀 手持%s 任务:%s", cause, heldItem, task);

        palace.addMemory(new MemoryItem(
                UUID.randomUUID(),
                MemoryCategory.EVENT,
                deathContent,
                Optional.of(maid.blockPosition()),
                Optional.ofNullable(maid.level().dimension().location().toString()),
                maid.level().getGameTime(),
                0,
                5
        ));
    }

    /**
     * TAIL: 清理全局 Map，防止内存泄漏。
     */
    @Inject(method = "remove", at = @At("TAIL"), remap = false)
    private void enhanced$cleanupMindPalace(Entity.RemovalReason reason, CallbackInfo ci) {
        UUID uuid = ((EntityMaid) (Object) this).getUUID();
        MindPalace.remove(uuid);
        ProactiveChatManager.reset(uuid);
    }

    // ==================== 主动聊天 ====================

    /**
     * 每 tick 检查主动聊天触发条件。
     */
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void enhanced$proactiveChatTick(CallbackInfo ci) {
        try {
            EntityMaid maid = (EntityMaid) (Object) this;
            if (!ProactiveChatManager.canTrigger(maid)) return;

            MaidAIChatManager chatManager = maid.getAiChatManager();
            LLMClient client = chatManager.getLLMSite().client();

            String systemPrompt = ProactiveChatCallback.buildProactivePrompt(maid);
            LLMMessage sysMsg = LLMMessage.systemChat(maid, systemPrompt);
            LLMMessage userMsg = LLMMessage.userChat(maid, "（主动发起对话）");
            List<LLMMessage> messages = List.of(sysMsg, userMsg);

            long waitingBubbleId = maid.getChatBubbleManager().addThinkingText("...");

            CompletableFuture<String> future = new CompletableFuture<>();
            ProactiveChatCallback callback = new ProactiveChatCallback(chatManager, messages, future);
            callback.setWaitingBubbleId(waitingBubbleId);

            client.chat(callback);
            ProactiveChatManager.markTriggered(maid.getUUID(), maid.level().getGameTime());

            if (EnhancedLittleMaidAI.DEBUG_LOG) {
                EnhancedLittleMaidAI.LOGGER.info(
                        "EnhancedLittleMaidAI: Proactive chat triggered for maid {} (#{})",
                        maid.getUUID(), ProactiveChatManager.getCount(maid.getUUID()));
            }
        } catch (Exception e) {
            EnhancedLittleMaidAI.LOGGER.warn("EnhancedLittleMaidAI: Proactive chat trigger failed", e);
        }
    }
}
