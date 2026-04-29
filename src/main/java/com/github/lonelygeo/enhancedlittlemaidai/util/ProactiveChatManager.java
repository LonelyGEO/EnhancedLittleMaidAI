package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 女仆主动聊天管理器。
 * 负责冷却计时、频率控制、触发条件检查，防止大量消耗 LLM token。
 *
 * <p>限制规则：
 * <ul>
 *   <li>好感度限制：仅好感度 >=2 级（朋友/挚友）时启用</li>
 *   <li>冷却时间：两次主动聊天之间最少间隔 10 分钟 (12000 ticks)</li>
 *   <li>触发概率：冷却结束后每次 tick 有 0.2% 概率触发（预期 ~25 秒触发）</li>
 *   <li>每日上限：每个 Minecraft 日最多 8 次主动聊天（日出清零）</li>
 *   <li>距离限制：主人玩家须在 10 格以内</li>
 *   <li>前置条件：LLM 站点已启用、主人是玩家且在线</li>
 * </ul>
 */
public final class ProactiveChatManager {

    private static final Map<UUID, Long> lastChatTime =
            Collections.synchronizedMap(new HashMap<>());
    private static final Map<UUID, Integer> dayChatCount =
            Collections.synchronizedMap(new HashMap<>());

    private ProactiveChatManager() {
    }

    /**
     * 检查是否可以触发主动聊天。
     * 每 tick 调用，所有检查都是 O(1)。
     *
     * @param maid 要检查的女仆
     * @return true 可以触发主动聊天
     */
    public static boolean canTrigger(EntityMaid maid) {
        if (!EnhancedConfig.PROACTIVE_CHAT_ENABLED.get()) return false;
        if (maid.level().isClientSide()) return false;
        if (maid.isRemoved()) return false;

        if (maid.getFavorabilityManager().getLevel() <= 1) return false;

        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer)) return false;
        double minDist = EnhancedConfig.MIN_PLAYER_DISTANCE.get();
        if (owner.distanceToSqr(maid) > minDist * minDist) return false;

        UUID uuid = maid.getUUID();
        long currentTime = maid.level().getGameTime();

        Long lastTime = lastChatTime.get(uuid);
        if (lastTime != null && (currentTime - lastTime) < EnhancedConfig.COOLDOWN_TICKS.get()) {
            return false;
        }

        int count = dayChatCount.getOrDefault(uuid, 0);
        if (count >= EnhancedConfig.MAX_CHATS_PER_DAY.get()) {
            return false;
        }

        if (Math.random() >= EnhancedConfig.TRIGGER_CHANCE_PER_TICK.get()) {
            return false;
        }

        return LLMUtil.isAvailable(maid);
    }

    /**
     * 标记主动聊天已触发，更新冷却时间和计数。
     */
    public static void markTriggered(UUID uuid, long gameTime) {
        lastChatTime.put(uuid, gameTime);
        dayChatCount.merge(uuid, 1, Integer::sum);
    }

    /** 获取本日已触发次数（调试用） */
    public static int getCount(UUID uuid) {
        return dayChatCount.getOrDefault(uuid, 0);
    }

    /** 日出时清零本日计数 */
    public static void resetDayCounts(UUID uuid) {
        dayChatCount.remove(uuid);
    }

    /** 清理指定女仆的所有状态（女仆移除/死亡时调用） */
    public static void reset(UUID uuid) {
        lastChatTime.remove(uuid);
        dayChatCount.remove(uuid);
    }
}
