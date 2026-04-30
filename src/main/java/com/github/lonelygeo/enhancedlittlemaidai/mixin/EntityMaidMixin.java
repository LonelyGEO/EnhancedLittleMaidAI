package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryCategory;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryItem;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.lonelygeo.enhancedlittlemaidai.util.EnvironmentEventDetector;
import com.github.lonelygeo.enhancedlittlemaidai.util.InterMaidChatCallback;
import com.github.lonelygeo.enhancedlittlemaidai.util.InterMaidChatManager;
import com.github.lonelygeo.enhancedlittlemaidai.util.InterMaidDecisionCallback;
import com.github.lonelygeo.enhancedlittlemaidai.util.LLMUtil;
import com.github.lonelygeo.enhancedlittlemaidai.util.MaidSpawnHandler;
import com.github.lonelygeo.enhancedlittlemaidai.util.ProactiveChatCallback;
import com.github.lonelygeo.enhancedlittlemaidai.util.ProactiveChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 女仆死亡时记录死亡记忆到 MindPalace；移除时清理全局 Map；tick 中触发主动聊天/环境事件。
 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidMixin {

    // ==================== MindPalace 磁盘持久化 ====================

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"), remap = false)
    private void enhanced$saveMindPalace(CompoundTag tag, CallbackInfo ci) {
        EntityMaid maid = (EntityMaid) (Object) this;
        MindPalace palace = MindPalace.get(maid.getUUID());
        if (palace != null && palace.size() > 0) {
            palace.writeToTag(tag);
            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.debug(
                        "EnhancedLittleMaidAI: Saved {} MindPalace memories for maid {}",
                        palace.size(), maid.getUUID());
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"), remap = false)
    private void enhanced$loadMindPalace(CompoundTag tag, CallbackInfo ci) {
        EntityMaid maid = (EntityMaid) (Object) this;
        MindPalace palace = MindPalace.getOrCreate(maid.getUUID());
        palace.readFromTag(tag);
        if (EnhancedConfig.debugLog() && palace.size() > 0) {
            EnhancedLittleMaidAI.LOGGER.debug(
                    "EnhancedLittleMaidAI: Loaded {} MindPalace memories for maid {}",
                    palace.size(), maid.getUUID());
        }
    }

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
        EnvironmentEventDetector.reset(uuid);
        InterMaidChatManager.reset(uuid);
        MaidSpawnHandler.clearGreeted(uuid);
    }

    // ==================== 主动聊天 ====================

    /**
     * 每 tick 检查触发条件。优先环境事件，其次原有概率流程。
     */
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void enhanced$proactiveChatTick(CallbackInfo ci) {
        try {
            EntityMaid maid = (EntityMaid) (Object) this;
            if (maid.level().isClientSide()) return;
            if (maid.isRemoved()) return;

            // ====== MaidSpawnHandler 延迟重试 ======
            MaidSpawnHandler.retryMaid(maid);

            // ====== 环境事件优先 ======
            EnvironmentEventDetector.EventType event = EnvironmentEventDetector.detect(maid);
            if (event != null) {
                UUID uuid = maid.getUUID();
                long gameTime = maid.level().getGameTime();

                // 日出时重置所有每日计数器
                if (event == EnvironmentEventDetector.EventType.SUNRISE) {
                    ProactiveChatManager.resetDayCounts(uuid);
                    EnvironmentEventDetector.resetDayCounts(uuid);
                    InterMaidChatManager.resetDayCounts(uuid);
                    InterMaidChatManager.resetGlobalDayCount();
                }

                if (!LLMUtil.isAvailable(maid)) return;

                int eventCount = EnvironmentEventDetector.getEventCount(uuid);
                if (eventCount >= EnhancedConfig.EVENT_MAX_PER_DAY.get()) return;
                if (!EnvironmentEventDetector.canTriggerEvent(uuid, gameTime,
                        EnhancedConfig.EVENT_COOLDOWN_TICKS.get(),
                        maid.blockPosition(), maid.level().dimension().location())) return;

                String eventDesc = EnvironmentEventDetector.toDescription(event, maid);
                String systemPrompt = ProactiveChatCallback.buildProactivePrompt(maid, eventDesc);
                if (triggerProactiveChat(maid, systemPrompt, "**少女感知中...**")) {
                    EnvironmentEventDetector.markTriggered(uuid, gameTime,
                            maid.blockPosition(), maid.level().dimension().location());
                    ProactiveChatManager.markTriggered(uuid, gameTime);
                    if (EnhancedConfig.debugLog()) {
                        EnhancedLittleMaidAI.LOGGER.info(
                                "EnhancedLittleMaidAI: Event-triggered chat for maid {} ({}), #{}",
                                uuid, event, eventCount + 1);
                    }
                }
                return;
            }

            // ====== 多女仆对话 ======
            UUID uuid = maid.getUUID();
            long gameTime = maid.level().getGameTime();

            if (!LLMUtil.isAvailable(maid)) return;

            // B 侧：检查 pending proposal
            UUID proposerUuid = InterMaidChatManager.getProposer(uuid);
            if (proposerUuid != null) {
                if (InterMaidChatManager.isProposalExpired(uuid, gameTime)) {
                    // 超时清理
                } else {
                    handleProposal(maid, proposerUuid, gameTime);
                }
            }

            // A 侧：降频扫描附近女仆（密度感知，避免女仆密集时触发过频）
            int interval = EnhancedConfig.INTER_MAID_SCAN_INTERVAL.get();
            if (gameTime % interval == 0 && InterMaidChatManager.canScan(maid, gameTime)) {
                int maxGroup = EnhancedConfig.INTER_MAID_MAX_GROUP_SIZE.get();
                List<EntityMaid> partners = InterMaidChatManager.findPartners(maid, maxGroup);
                if (!partners.isEmpty()) {
                    // 密度感知: 附近女仆越多，单个女仆触发概率越低
                    // partners=1 → scale=1.0  partners=4 → 0.4  partners=9 → 0.2
                    double densityScale = 2.0 / (partners.size() + 1);
                    if (Math.random() <= densityScale) {
                        List<UUID> targets = partners.stream()
                                .map(EntityMaid::getUUID).toList();
                        InterMaidChatManager.proposeGroup(uuid, targets, gameTime);
                        if (EnhancedConfig.debugLog()) {
                            EnhancedLittleMaidAI.LOGGER.info(
                                    "InterMaidChat: Maid {} proposed to {} targets", uuid, targets.size());
                        }
                        long scanBubbleId = maid.getChatBubbleManager()
                                .addTextChatBubble("**少女寻友中...**");
                        new Thread(() -> {
                            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                            net.minecraft.server.MinecraftServer srv = maid.getServer();
                            if (srv != null && !maid.isRemoved()) {
                                srv.submit(() -> maid.getChatBubbleManager()
                                        .removeChatBubble(scanBubbleId));
                            }
                        }).start();
                    } else if (EnhancedConfig.debugLog()) {
                        EnhancedLittleMaidAI.LOGGER.debug(
                                "InterMaidChat: density skip, partners={}", partners.size());
                    }
                }
            }

            // ====== 原有概率流程 ======
            if (!ProactiveChatManager.canTrigger(maid)) return;

            String systemPrompt = ProactiveChatCallback.buildProactivePrompt(maid);
            if (triggerProactiveChat(maid, systemPrompt, "**少女思考中...**")) {
                ProactiveChatManager.markTriggered(maid.getUUID(), maid.level().getGameTime());
                if (EnhancedConfig.debugLog()) {
                    EnhancedLittleMaidAI.LOGGER.info(
                            "EnhancedLittleMaidAI: Proactive chat triggered for maid {} (#{})",
                            maid.getUUID(), ProactiveChatManager.getCount(maid.getUUID()));
                }
            }
        } catch (Exception e) {
            EnhancedLittleMaidAI.LOGGER.warn("EnhancedLittleMaidAI: Proactive chat trigger failed", e);
        }
    }

    /**
     * 执行一次主动聊天 LLM 调用。返回 true 表示发送成功。
     */
    private static boolean triggerProactiveChat(EntityMaid maid, String systemPrompt, String thinkingText) {
        try {
            MaidAIChatManager chatManager = maid.getAiChatManager();
            LLMClient client = chatManager.getLLMSite().client();

            LLMMessage sysMsg = LLMMessage.systemChat(maid, systemPrompt);
            LLMMessage userMsg = LLMMessage.userChat(maid, "（主动发起对话）");
            List<LLMMessage> messages = List.of(sysMsg, userMsg);

            long waitingBubbleId = maid.getChatBubbleManager().addThinkingText(thinkingText);

            CompletableFuture<String> future = new CompletableFuture<>();
            ProactiveChatCallback callback = new ProactiveChatCallback(chatManager, messages, future);
            callback.setWaitingBubbleId(waitingBubbleId);

            client.chat(callback);
            return true;
        } catch (Exception e) {
            EnhancedLittleMaidAI.LOGGER.warn("EnhancedLittleMaidAI: triggerProactiveChat failed", e);
            return false;
        }
    }

    /**
     * 处理 B 收到的对话提案。决策后触发或拒绝。
     */
    private static void handleProposal(EntityMaid b, UUID proposerUuid, long gameTime) {
        EntityMaid a = findMaidByUuid(b, proposerUuid);
        if (a == null || a.isRemoved()) {
            InterMaidChatManager.rejectFromGroup(b.getUUID());
            return;
        }

        if (!InterMaidChatManager.canAccept(b, a, gameTime)) {
            // LLM 不可用导致拒绝时不写 pair cooldown，避免阻塞后续正常对话
            if (LLMUtil.isAvailable(b)) {
                InterMaidChatManager.markRejected(a.getUUID(), b.getUUID(), gameTime);
            } else {
                InterMaidChatManager.rejectFromGroup(b.getUUID());
                if (EnhancedConfig.debugLog()) {
                    EnhancedLittleMaidAI.LOGGER.debug(
                            "InterMaidChat: LLM unavailable for {}, soft reject", b.getUUID());
                }
            }
            return;
        }

        if (InterMaidChatManager.isDeciding(b.getUUID()) || InterMaidChatManager.isDeciding(a.getUUID())) {
            return;
        }

        if (!InterMaidChatManager.tryAcquireDecisionSlot()) {
            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.debug(
                        "InterMaidChat: Decision slot full, delaying {} ← {}",
                        b.getUUID(), a.getUUID());
            }
            return;
        }

        String decisionMode = EnhancedConfig.INTER_MAID_DECISION_MODE.get();
        if ("LLM".equals(decisionMode)) {
            InterMaidChatManager.startDeciding(b.getUUID());
            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.info(
                        "InterMaidChat: B {} asking LLM to decide on proposal from {}",
                        b.getUUID(), a.getUUID());
            }
            String prompt = InterMaidDecisionCallback.buildDecisionPrompt(b, a);
            LLMMessage sysMsg = LLMMessage.systemChat(b, prompt);
            LLMMessage userMsg = LLMMessage.userChat(b, "（接受聊天请求）");
            List<LLMMessage> msgs = List.of(sysMsg, userMsg);
            MaidAIChatManager mgr = b.getAiChatManager();
            InterMaidDecisionCallback cb = new InterMaidDecisionCallback(mgr, msgs, a, b);
            mgr.getLLMSite().client().chat(cb);
        } else {
            // WEIGHT mode
            if (InterMaidChatManager.decideByWeight(b, a)) {
                InterMaidChatManager.releaseDecisionSlot();
                acceptAndTryStart(b, a);
            } else {
                InterMaidChatManager.releaseDecisionSlot();
                InterMaidChatManager.markRejected(a.getUUID(), b.getUUID(), gameTime);
            }
        }
    }

    /** B 接受提案 → 加入群组 → 足够人时启动对话 */
    private static void acceptAndTryStart(EntityMaid b, EntityMaid a) {
        InterMaidChatManager.handleAcceptance(b);
    }

    @Nullable
    private static EntityMaid findMaidByUuid(EntityMaid maid, UUID uuid) {
        for (EntityMaid e : maid.level().getEntitiesOfClass(
                EntityMaid.class, maid.getBoundingBox().inflate(64),
                e -> e.getUUID().equals(uuid))) {
            return e;
        }
        return null;
    }

    // === import for Nullable ===
}
