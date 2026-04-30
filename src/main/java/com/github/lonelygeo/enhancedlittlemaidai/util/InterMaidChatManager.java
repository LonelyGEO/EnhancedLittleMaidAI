package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final Map<UUID, Long> INITIAL_SCAN_DELAYS =
            new ConcurrentHashMap<>();
    // 每女仆每日计数
    private static final Map<UUID, Integer> DAY_COUNTS =
            Collections.synchronizedMap(new HashMap<>());
    // 全局每日计数
    private static final AtomicInteger globalDayCount = new AtomicInteger();
    // pending proposals: toUUID → Proposal
    private static final Map<UUID, Proposal> PENDING_PROPOSALS =
            Collections.synchronizedMap(new HashMap<>());
    // group proposals: initiator UUID → GroupProposal (pending acceptors)
    private static final Map<UUID, GroupProposal> GROUP_PROPOSALS =
            Collections.synchronizedMap(new HashMap<>());
    // busy set
    private static final Set<UUID> BUSY = Collections.synchronizedSet(new HashSet<>());
    // deciding set: B 正在等 LLM 决策，防止每个 tick 重复触发请求
    private static final Set<UUID> DECIDING = Collections.synchronizedSet(new HashSet<>());
    // 全局并发决策计数，防止多对女仆同时向 LLM 发起决策请求
    private static final AtomicInteger DECIDING_COUNT = new AtomicInteger(0);
    private static final int MAX_DECIDING = 2;

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

        AABB box = self.getBoundingBox().inflate(range);
        List<EntityMaid> list = self.level().getEntitiesOfClass(
                EntityMaid.class, box,
                e -> e.isAlive() && !e.isRemoved()
                        && e != self
                        && e.isTame()
                        && (EnhancedConfig.INTER_MAID_CROSS_OWNER.get()
                            || (ownerUuid != null && ownerUuid.equals(e.getOwnerUUID())))
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

    /** 创建群组提案：A 向多个 B 发起邀请 */
    public static void proposeGroup(UUID from, List<UUID> targets, long gameTime) {
        GROUP_PROPOSALS.put(from,
                new GroupProposal(from, new LinkedHashSet<>(targets), gameTime));
        for (UUID to : targets) {
            PENDING_PROPOSALS.put(to, new Proposal(from, gameTime));
        }
    }

    /** B 接受提案 → 加入群组。返回当前群组成员（含 A），不足 2 人时返回 null */
    @Nullable
    public static List<UUID> acceptIntoGroup(UUID acceptor) {
        Proposal p = PENDING_PROPOSALS.remove(acceptor);
        if (p == null) return null;
        GroupProposal gp = GROUP_PROPOSALS.get(p.from);
        if (gp == null) return null;
        gp.accepted.add(acceptor);
        gp.targets.remove(acceptor);

        List<UUID> all = new ArrayList<>();
        all.add(p.from); // A
        all.addAll(gp.accepted); // all B's that accepted
        return all.size() >= 2 ? List.copyOf(all) : null;
    }

    /** 提案被拒绝 → 从目标集移除 */
    public static void rejectFromGroup(UUID rejected) {
        Proposal p = PENDING_PROPOSALS.remove(rejected);
        if (p == null) return;
        GroupProposal gp = GROUP_PROPOSALS.get(p.from);
        if (gp == null) return;
        gp.targets.remove(rejected);
        if (gp.targets.isEmpty() && gp.accepted.isEmpty()) {
            GROUP_PROPOSALS.remove(p.from);
        }
    }

    public static void cleanupGroup(UUID initiator) {
        GROUP_PROPOSALS.remove(initiator);
    }

    /** 尝试启动群组对话 — 收集参与者实体并按距玩家排序后启动 */
    public static void tryStartConversation(List<UUID> memberUuids, net.minecraft.world.level.Level level) {
        List<EntityMaid> participants = new ArrayList<>();
        for (UUID uid : memberUuids) {
            double playerDist = EnhancedConfig.INTER_MAID_PLAYER_DISTANCE.get();
            for (EntityMaid m : level.getEntitiesOfClass(
                    EntityMaid.class, new AABB(0, -64, 0, 30000000, 320, 30000000),
                    e -> e.getUUID().equals(uid))) {
                participants.add(m);
                break;
            }
        }
        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.debug(
                    "InterMaidChat: tryStartConversation found {}/{} entities",
                    participants.size(), memberUuids.size());
        }
        if (participants.size() >= 2) {
            participants.sort(Comparator.comparingDouble(m -> {
                double dist = EnhancedConfig.INTER_MAID_PLAYER_DISTANCE.get();
                AABB box = m.getBoundingBox().inflate(dist);
                return m.level().getEntitiesOfClass(ServerPlayer.class, box, Player::isAlive)
                        .stream().mapToDouble(p -> p.distanceToSqr(m)).min().orElse(Double.MAX_VALUE);
            }));
            markBusy(participants);
            UUID initiator = participants.get(0).getUUID();
            cleanupGroup(initiator);
            InterMaidChatCallback.startConversation(participants,
                    EnhancedConfig.INTER_MAID_MAX_ROUNDS.get());
        }
    }

    /** 标记多名女仆忙碌 */
    public static void markBusy(List<EntityMaid> maids) {
        for (EntityMaid m : maids) BUSY.add(m.getUUID());
    }

    /** B 接受提案 → 加入群组，人数够时启动对话 */
    public static void handleAcceptance(EntityMaid b) {
        List<UUID> members = acceptIntoGroup(b.getUUID());
        if (members != null && members.size() >= 2) {
            tryStartConversation(members, b.level());
        }
    }

    /** 原子抢出 proposal（forkJoin 线程安全），返回 proposer UUID 或 null */
    @Nullable
    public static UUID claimProposal(UUID acceptor) {
        Proposal p = PENDING_PROPOSALS.remove(acceptor);
        if (p == null) return null;
        return p.from;
    }

    /** 在 server thread 上完成接受：加入群组 → 人数够时启动对话 */
    public static void finalizeAcceptance(EntityMaid b, UUID proposerUuid) {
        GroupProposal gp = GROUP_PROPOSALS.get(proposerUuid);
        if (gp == null) {
            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.debug(
                        "InterMaidChat: finalizeAcceptance no group found for proposer {}", proposerUuid);
            }
            return;
        }
        gp.accepted.add(b.getUUID());
        gp.targets.remove(b.getUUID());

        List<UUID> all = new ArrayList<>();
        all.add(proposerUuid);
        all.addAll(gp.accepted);
        if (all.size() >= 2) {
            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.debug(
                        "InterMaidChat: finalizeAcceptance starting, group: {} members", all.size());
            }
            tryStartConversation(List.copyOf(all), b.level());
        } else if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.debug(
                    "InterMaidChat: finalizeAcceptance group too small: {} members", all.size());
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
        if (!LLMUtil.isAvailable(maid)) return false;
        if (!EnhancedConfig.INTER_MAID_ENABLED.get()) return false;
        if (BUSY.contains(maid.getUUID())) return false;

        UUID uuid = maid.getUUID();

        // 启动抖动：首次放置后随机延迟 0-600 tick
        long delay = INITIAL_SCAN_DELAYS.computeIfAbsent(uuid,
                k -> gameTime + (long) (Math.random() * 600));
        if (gameTime < delay) return false;
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
        if (!LLMUtil.isAvailable(b)) return false;
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
        INITIAL_SCAN_DELAYS.remove(uuid);
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

    // ==================== 决策互斥 ====================

    public static boolean isDeciding(UUID uuid) {
        return DECIDING.contains(uuid);
    }

    public static void startDeciding(UUID uuid) {
        DECIDING.add(uuid);
    }

    public static void finishDeciding(UUID uuid) {
        DECIDING.remove(uuid);
    }

    public static boolean tryAcquireDecisionSlot() {
        int current = DECIDING_COUNT.get();
        if (current >= MAX_DECIDING) {
            return false;
        }
        return DECIDING_COUNT.compareAndSet(current, current + 1)
                || tryAcquireDecisionSlot();
    }

    public static void releaseDecisionSlot() {
        DECIDING_COUNT.decrementAndGet();
    }

    // ==================== 内部类型 ====================

    private record Proposal(UUID from, long time) {
    }

    private static final class GroupProposal {
        final UUID initiator;
        final Set<UUID> targets;
        final Set<UUID> accepted = new LinkedHashSet<>();
        final long time;

        GroupProposal(UUID initiator, Set<UUID> targets, long time) {
            this.initiator = initiator;
            this.targets = targets;
            this.time = time;
        }
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
