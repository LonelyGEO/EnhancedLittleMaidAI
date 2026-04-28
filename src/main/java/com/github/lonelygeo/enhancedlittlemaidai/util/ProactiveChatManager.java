package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMSite;
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
 *   <li>冷却时间：两次主动聊天之间最少间隔 10 分钟 (12000 ticks)</li>
 *   <li>触发概率：冷却结束后每次 tick 有 0.2% 概率触发（预期 ~25 秒触发）</li>
 *   <li>会话上限：每次游戏会话最多 8 次主动聊天</li>
 *   <li>距离限制：主人玩家须在 10 格以内</li>
 *   <li>前置条件：LLM 站点已启用、主人是玩家且在线</li>
 * </ul>
 */
public final class ProactiveChatManager {

    /** 两次主动聊天之间最少间隔（tick） */
    private static final long COOLDOWN_TICKS = 12000;
    /** 冷却结束后每次 tick 触发概率 */
    private static final double TRIGGER_CHANCE_PER_TICK = 0.002;
    /** 每次游戏会话最多主动聊天次数 */
    private static final int MAX_CHATS_PER_SESSION = 8;
    /** 主人玩家须在 10 格内（平方距离） */
    private static final double MIN_PLAYER_DISTANCE_SQ = 100.0;

    private static final Map<UUID, Long> lastChatTime =
            Collections.synchronizedMap(new HashMap<>());
    private static final Map<UUID, Integer> chatCount =
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
        if (maid.level().isClientSide()) return false;
        if (maid.isRemoved()) return false;

        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer)) return false;
        if (owner.distanceToSqr(maid) > MIN_PLAYER_DISTANCE_SQ) return false;

        UUID uuid = maid.getUUID();
        long currentTime = maid.level().getGameTime();

        Long lastTime = lastChatTime.get(uuid);
        if (lastTime != null && (currentTime - lastTime) < COOLDOWN_TICKS) {
            return false;
        }

        int count = chatCount.getOrDefault(uuid, 0);
        if (count >= MAX_CHATS_PER_SESSION) {
            return false;
        }

        if (Math.random() >= TRIGGER_CHANCE_PER_TICK) {
            return false;
        }

        MaidAIChatManager chatManager = maid.getAiChatManager();
        if (chatManager == null) return false;
        LLMSite site = chatManager.getLLMSite();
        if (site == null || !site.enabled()) return false;
        LLMClient client = site.client();
        if (client == null) return false;

        return true;
    }

    /**
     * 标记主动聊天已触发，更新冷却时间和计数。
     *
     * @param uuid     女仆 UUID
     * @param gameTime 当前游戏时间
     */
    public static void markTriggered(UUID uuid, long gameTime) {
        lastChatTime.put(uuid, gameTime);
        chatCount.merge(uuid, 1, Integer::sum);
    }

    /** 获取当前会话已触发次数（调试用） */
    public static int getCount(UUID uuid) {
        return chatCount.getOrDefault(uuid, 0);
    }

    /** 清理指定女仆的所有状态（女仆移除/死亡时调用） */
    public static void reset(UUID uuid) {
        lastChatTime.remove(uuid);
        chatCount.remove(uuid);
    }
}
