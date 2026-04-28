package com.github.lonelygeo.enhancedlittlemaidai.util;

/**
 * 游戏日相位工具。
 * 将 dayTime % 24000 映射为四个阶段。
 */
final class DayPhaseUtil {
    private DayPhaseUtil() {}

    /** 0=清晨, 1=白天, 2=黄昏, 3=夜晚 */
    static int getDayPhase(long dayTime) {
        if (dayTime < 6000) return 0;
        if (dayTime < 12000) return 1;
        if (dayTime < 13000) return 2;
        return 3;
    }
}
