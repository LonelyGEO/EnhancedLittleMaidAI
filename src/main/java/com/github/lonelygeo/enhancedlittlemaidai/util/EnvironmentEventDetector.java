package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 环境事件检测器。
 * 比较女仆当前环境状态与上一 tick 快照，检测日出、日落、天气变化、生物群系切换。
 * 事件触发独立于概率性主动聊天，有单独的冷却和次数上限。
 */
public final class EnvironmentEventDetector {

    private static final Map<UUID, Snapshot> SNAPSHOTS =
            Collections.synchronizedMap(new HashMap<>());
    private static final Map<UUID, Long> LAST_EVENT_TIME =
            Collections.synchronizedMap(new HashMap<>());
    private static final Map<UUID, Integer> DAY_EVENT_COUNT =
            Collections.synchronizedMap(new HashMap<>());
    // 全局冷却：防止多女仆同时触发同一事件
    private static long lastGlobalEventTime = -1;
    private static final long GLOBAL_COOLDOWN_TICKS = 200; // 10 秒
    // 每女仆启动抖动：世界加载后随机延迟 0-400 tick 再开始检测
    private static final Map<UUID, Long> STARTUP_DELAYS = new ConcurrentHashMap<>();

    private EnvironmentEventDetector() {
    }

    /** 事件类型 */
    public enum EventType {
        SUNRISE, SUNSET,
        RAIN_START, RAIN_END,
        THUNDER_START, THUNDER_END,
        BIOME_CHANGE
    }

    /** 检测状态变化，返回事件类型或 null */
    public static EventType detect(EntityMaid maid) {
        try {
            UUID uuid = maid.getUUID();
            long gameTime = maid.level().getGameTime();

            // 启动抖动：世界加载后延迟检测
            long startupDelay = STARTUP_DELAYS.computeIfAbsent(uuid,
                    k -> gameTime + (long)(Math.random() * 400));
            if (gameTime < startupDelay) return null;

            boolean hasPrecip = maid.level().getBiome(maid.blockPosition()).value().hasPrecipitation();
            boolean raining = maid.level().isRaining() && hasPrecip;
            boolean thundering = maid.level().isThundering() && hasPrecip;
            int dayPhase = getDayPhase(maid.level().getDayTime() % 24000);
            ResourceLocation biome = maid.level().getBiome(maid.blockPosition()).unwrapKey()
                    .map(k -> k.location()).orElse(null);

            Snapshot prev = SNAPSHOTS.put(uuid, new Snapshot(raining, thundering, dayPhase, biome));
            if (prev == null) return null; // 第一帧，无历史

            if (prev.dayPhase != dayPhase) {
                if (dayPhase == 0) return EventType.SUNRISE;      // 进入清晨
                if (dayPhase == 2) return EventType.SUNSET;        // 进入黄昏
            }

            if (raining && !prev.raining) return EventType.RAIN_START;
            if (!raining && prev.raining) return EventType.RAIN_END;

            if (thundering && !prev.thundering) return EventType.THUNDER_START;
            if (!thundering && prev.thundering) return EventType.THUNDER_END;

            if (biome != null && !biome.equals(prev.biome)) return EventType.BIOME_CHANGE;

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 将游戏时间映射为阶段: 0=清晨(0-6000), 1=白天(6000-12000), 2=黄昏(12000-13000), 3=夜晚(13000-24000) */
    static int getDayPhase(long dayTime) {
        return DayPhaseUtil.getDayPhase(dayTime);
    }

    /** 事件名称转中文描述 */
    public static String toDescription(EventType type, EntityMaid maid) {
        String biome = "未知";
        try {
            var key = maid.level().getBiome(maid.blockPosition()).unwrapKey();
            if (key.isPresent()) {
                biome = key.get().location().getPath().replace('_', ' ');
            }
        } catch (Exception ignored) {
        }

        return switch (type) {
            case SUNRISE -> "现在是清晨日出时分。";
            case SUNSET -> "现在是黄昏日落时分。";
            case RAIN_START -> "天开始下雨了。";
            case RAIN_END -> "雨停了。";
            case THUNDER_START -> "打雷了，雷暴开始了。";
            case THUNDER_END -> "雷暴结束了。";
            case BIOME_CHANGE -> "我们来到了" + biome + "。";
        };
    }

    /** 检查事件冷却是否已过 */
    public static boolean canTriggerEvent(UUID uuid, long gameTime, long cooldownTicks) {
        if ((gameTime - lastGlobalEventTime) < GLOBAL_COOLDOWN_TICKS) return false;
        Long last = LAST_EVENT_TIME.get(uuid);
        return last == null || (gameTime - last) >= cooldownTicks;
    }

    /** 标记事件已触发 */
    public static void markTriggered(UUID uuid, long gameTime) {
        LAST_EVENT_TIME.put(uuid, gameTime);
        lastGlobalEventTime = gameTime;
        DAY_EVENT_COUNT.merge(uuid, 1, Integer::sum);
    }

    /** 本日事件触发次数 */
    public static int getEventCount(UUID uuid) {
        return DAY_EVENT_COUNT.getOrDefault(uuid, 0);
    }

    /** 日出时清零本日事件计数 */
    public static void resetDayCounts(UUID uuid) {
        DAY_EVENT_COUNT.remove(uuid);
    }

    /** 清理女仆状态 */
    public static void reset(UUID uuid) {
        SNAPSHOTS.remove(uuid);
        LAST_EVENT_TIME.remove(uuid);
        DAY_EVENT_COUNT.remove(uuid);
    }

    private record Snapshot(boolean raining, boolean thundering, int dayPhase, ResourceLocation biome) {
    }
}
