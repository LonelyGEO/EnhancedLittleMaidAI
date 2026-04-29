package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多女仆协调对话管理器。
 * 负责扫描附近女仆、配对提案、busy 管理、冷却/每日计数。
 * 当前版本支持 2 人对话，接口已预留 N 人扩展。
 */
public final class InterMaidChatManager {

    private static final long PROPOSAL_TIMEOUT = 200; // 10 秒
    private static final double SELECTION_RANGE_MULTIPLIER = 2.0; // 扫描实际范围放大倍数（给对方移动空间）

    // 同对冷却: key = "sorted_UUIDa|UUIDb"
    private static final Map<String, Long> PAIR_COOLDOWNS =
            Collections.synchronizedMap(new HashMap<>());
    // 每女仆每日计数
    private static final Map<UUID, Integer> DAY_COUNTS =
            Collections.synchronizedMap(new HashMap<>());
    // 全局每日计数
    private static final AtomicInteger globalDayCount = new AtomicInteger();
    // pending proposals: toUUID → Proposal
    private static final Map<UUID, Proposal> PENDING_PROPOSALS =
            Collections.synchronizedMap(new HashMap<>());
    // busy set
    private static final Set<UUID> BUSY = Collections.synchronizedSet(new HashSet<>());

    private InterMaidChatManager() {
    }

    // ==================== 扫描 ====================

    /**
     * 扫描附近符合条件的女仆，返回最近的一个或 null。
     */
    @Nullable
    public static EntityMaid findPartner(EntityMaid maid) {
        double triggerDist = getTriggerDistance(maid);
        double scanRange = triggerDist * SELECTION_RANGE_MULTIPLIER;
        List<EntityMaid> candidates = scanNearbyMaids(maid, scanRange);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * 扫描附近已驯服的同主人女仆（排除自己、忙碌的）。
     * 返回按距离排序的列表。内部方法，已返回 List 便于未来 N 人扩展。
     */
    private static List<EntityMaid> scanNearbyMaids(EntityMaid self, double range) {
        UUID ownerUuid = self.getOwnerUUID();
        if (ownerUuid == null) return List.of();

        AABB box = self.getBoundingBox().inflate(range);
        List<EntityMaid> list = self.level().getEntitiesOfClass(
                EntityMaid.class, box,
                e -> e.isAlive() && !e.isRemoved()
                        && e != self
                        && e.isTame()
                        && ownerUuid.equals(e.getOwnerUUID())
                        && !BUSY.contains(e.getUUID())
        );
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(self)));
        return list;
    }

    // ==================== 提案系统 ====================

    public static void propose(UUID from, UUID to, long gameTime) {
        PENDING_PROPOSALS.put(to, new Proposal(from, gameTime));
        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.info("InterMaidChat: Proposal {} → {}", from, to);
        }
    }

    @Nullable
    public static UUID getProposer(UUID maid) {
        Proposal p = PENDING_PROPOSALS.get(maid);
        if (p == null) return null;
        return p.from;
    }

    /** 检查是否超时，超时则清理 */
    public static boolean isProposalExpired(UUID maid, long gameTime) {
        Proposal p = PENDING_PROPOSALS.get(maid);
        if (p != null && (gameTime - p.time) > PROPOSAL_TIMEOUT) {
            PENDING_PROPOSALS.remove(maid);
            return true;
        }
        return false;
    }

    public static void clearProposal(UUID maid) {
        PENDING_PROPOSALS.remove(maid);
    }

    // ==================== 忙闲管理 ====================

    public static void markBusy(UUID a, UUID b) {
        BUSY.add(a);
        BUSY.add(b);
    }

    public static void releaseBusy(UUID a, UUID b) {
        BUSY.remove(a);
        BUSY.remove(b);
        clearProposal(a);
        clearProposal(b);
    }

    public static boolean isBusy(UUID maid) {
        return BUSY.contains(maid);
    }

    // ==================== 触发条件 ====================

    /**
     * 综合检查 A 是否满足扫描条件。
     */
    public static boolean canScan(EntityMaid maid, long gameTime) {
        if (maid.level().isClientSide()) return false;
        if (maid.isRemoved()) return false;
        if (!EnhancedConfig.INTER_MAID_ENABLED.get()) return false;
        if (BUSY.contains(maid.getUUID())) return false;

        UUID uuid = maid.getUUID();
        if (DAY_COUNTS.getOrDefault(uuid, 0) >= EnhancedConfig.INTER_MAID_MAX_PER_DAY.get()) return false;
        if (globalDayCount.get() >= EnhancedConfig.INTER_MAID_MAX_GLOBAL_PER_DAY.get()) return false;

        // 附近有任意玩家？
        double playerDist = EnhancedConfig.INTER_MAID_PLAYER_DISTANCE.get();
        AABB box = maid.getBoundingBox().inflate(playerDist);
        List<ServerPlayer> players = maid.level().getEntitiesOfClass(
                ServerPlayer.class, box, Player::isAlive);
        return !players.isEmpty();
    }

    /**
     * 检查 B 是否满足接受条件（冷却/上限/距离）。
     */
    public static boolean canAccept(EntityMaid b, EntityMaid a, long gameTime) {
        if (b.level().isClientSide()) return false;
        if (b.isRemoved()) return false;
        if (BUSY.contains(b.getUUID())) return false;

        UUID bUuid = b.getUUID();
        if (DAY_COUNTS.getOrDefault(bUuid, 0) >= EnhancedConfig.INTER_MAID_MAX_PER_DAY.get()) return false;

        // 同对冷却
        String pairKey = pairKey(a.getUUID(), b.getUUID());
        Long last = PAIR_COOLDOWNS.get(pairKey);
        if (last != null && (gameTime - last) < EnhancedConfig.INTER_MAID_COOLDOWN_TICKS.get()) return false;

        // 距离 — B 自己也要在 A 附近（work/idle 距离判断由 B 状态决定）
        double dist = a.distanceTo(b);
        double triggerDist = getTriggerDistance(b);
        return dist <= triggerDist;
    }

    /** 工作中/空闲不同距离 */
    public static double getTriggerDistance(EntityMaid maid) {
        return isWorking(maid)
                ? EnhancedConfig.INTER_MAID_WORKING_DISTANCE.get()
                : EnhancedConfig.INTER_MAID_IDLE_DISTANCE.get();
    }

    /** 工作中/空闲不同概率 */
    public static double getTriggerChance(EntityMaid maid) {
        return isWorking(maid)
                ? EnhancedConfig.INTER_MAID_WORKING_CHANCE.get()
                : EnhancedConfig.INTER_MAID_IDLE_CHANCE.get();
    }

    private static boolean isWorking(EntityMaid maid) {
        try {
            var task = maid.getTask();
            if (task == null) return false;
            String uid = task.getUid().toString();
            return !"touhou_little_maid:idle".equals(uid);
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 决策模式 ====================

    /**
     * 计算 WEIGHT 模式下 B 的接受概率。
     * baseChance × 记忆加权。
     */
    public static double computeAcceptWeight(EntityMaid b, EntityMaid a) {
        double baseChance = getTriggerChance(b);
        // 记忆加权（未来 10b 实现）
        return Math.min(baseChance * 1.5, 0.3);
    }

    public static boolean decideByWeight(EntityMaid b, EntityMaid a) {
        return Math.random() < computeAcceptWeight(b, a);
    }

    // ==================== 计数/冷却 ====================

    public static void markTriggered(UUID a, UUID b, long gameTime) {
        PAIR_COOLDOWNS.put(pairKey(a, b), gameTime);
        DAY_COUNTS.merge(a, 1, Integer::sum);
        DAY_COUNTS.merge(b, 1, Integer::sum);
        globalDayCount.incrementAndGet();
        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.info(
                    "InterMaidChat: Triggered {}↔{}, dayCount={}, globalCount={}",
                    a, b, getDayCount(a), getGlobalDayCount());
        }
    }

    public static void markRejected(UUID a, UUID b, long gameTime) {
        PAIR_COOLDOWNS.put(pairKey(a, b), gameTime);
        clearProposal(b);
        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.info("InterMaidChat: Rejected {} → {}", a, b);
        }
    }

    static String pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    public static void resetDayCounts(UUID uuid) {
        DAY_COUNTS.remove(uuid);
    }

    public static void resetGlobalDayCount() {
        globalDayCount.set(0);
    }

    public static void reset(UUID uuid) {
        PAIR_COOLDOWNS.entrySet().removeIf(e -> {
            String[] parts = e.getKey().split("\\|");
            String id = uuid.toString();
            return parts.length == 2 && (parts[0].equals(id) || parts[1].equals(id));
        });
        DAY_COUNTS.remove(uuid);
        PENDING_PROPOSALS.remove(uuid);
        BUSY.remove(uuid);
    }

    // ==================== 公共 getter (调试用) ====================

    public static int getDayCount(UUID uuid) {
        return DAY_COUNTS.getOrDefault(uuid, 0);
    }

    public static int getGlobalDayCount() {
        return globalDayCount.get();
    }

    public static int getPendingProposalCount() {
        return PENDING_PROPOSALS.size();
    }

    public static int getBusyCount() {
        return BUSY.size();
    }

    // ==================== 内部类型 ====================

    private record Proposal(UUID from, long time) {
    }

    /** 未来 N 人扩展接口 */
    public static void proposeBatch(UUID from, List<UUID> toList, long gameTime) {
        for (UUID to : toList) {
            propose(from, to, gameTime);
        }
    }

    /** 未来 N 人扩展接口 */
    public static List<EntityMaid> findPartners(EntityMaid maid, int maxGroupSize) {
        double scanRange = getTriggerDistance(maid) * SELECTION_RANGE_MULTIPLIER;
        List<EntityMaid> all = scanNearbyMaids(maid, scanRange);
        return all.subList(0, Math.min(maxGroupSize - 1, all.size()));
    }
}
