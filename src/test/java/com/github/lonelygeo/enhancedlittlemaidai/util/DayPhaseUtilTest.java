package com.github.lonelygeo.enhancedlittlemaidai.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * DayPhaseUtil 单元测试 — gameTime % 24000 → 四个 phase 映射。
 */
public class DayPhaseUtilTest {

    @Test
    public void testDawn() {
        assertEquals(0, DayPhaseUtil.getDayPhase(0));
        assertEquals(0, DayPhaseUtil.getDayPhase(1000));
        assertEquals(0, DayPhaseUtil.getDayPhase(5999));
    }

    @Test
    public void testDaytime() {
        assertEquals(1, DayPhaseUtil.getDayPhase(6000));
        assertEquals(1, DayPhaseUtil.getDayPhase(10000));
        assertEquals(1, DayPhaseUtil.getDayPhase(11999));
    }

    @Test
    public void testDusk() {
        assertEquals(2, DayPhaseUtil.getDayPhase(12000));
        assertEquals(2, DayPhaseUtil.getDayPhase(12999));
    }

    @Test
    public void testNight() {
        assertEquals(3, DayPhaseUtil.getDayPhase(13000));
        assertEquals(3, DayPhaseUtil.getDayPhase(18000));
        assertEquals(3, DayPhaseUtil.getDayPhase(23999));
    }

    @Test
    public void testCrossMidnight() {
        assertEquals(3, DayPhaseUtil.getDayPhase(23999));
        assertEquals(0, DayPhaseUtil.getDayPhase(0));
    }

    @Test
    public void testWrapBeyondDay() {
        assertEquals(0, DayPhaseUtil.getDayPhase(24000 % 24000));
        assertEquals(1, DayPhaseUtil.getDayPhase((24000 + 6000) % 24000));
        assertEquals(3, DayPhaseUtil.getDayPhase((24000 + 13000) % 24000));
    }
}
