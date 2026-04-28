package com.github.lonelygeo.enhancedlittlemaidai.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class EnvironmentEventDetectorTest {

    @Test
    public void testDayPhaseDawn() {
        assertEquals(0, callGetDayPhase(0));
        assertEquals(0, callGetDayPhase(1000));
        assertEquals(0, callGetDayPhase(5999));
    }

    @Test
    public void testDayPhaseDaytime() {
        assertEquals(1, callGetDayPhase(6000));
        assertEquals(1, callGetDayPhase(10000));
        assertEquals(1, callGetDayPhase(11999));
    }

    @Test
    public void testDayPhaseDusk() {
        assertEquals(2, callGetDayPhase(12000));
        assertEquals(2, callGetDayPhase(12500));
        assertEquals(2, callGetDayPhase(12999));
    }

    @Test
    public void testDayPhaseNight() {
        assertEquals(3, callGetDayPhase(13000));
        assertEquals(3, callGetDayPhase(18000));
        assertEquals(3, callGetDayPhase(23999));
    }

    @Test
    public void testDayPhaseCrossMidnight() {
        assertEquals(3, callGetDayPhase(23999));
        assertEquals(0, callGetDayPhase(0));
    }

    @Test
    public void testToDescriptionHasExpectedKeys() {
        for (EnvironmentEventDetector.EventType type : EnvironmentEventDetector.EventType.values()) {
            String desc = EnvironmentEventDetector.toDescription(type, null);
            assertNotNull(desc);
            assertFalse(desc.isEmpty());
            switch (type) {
                case SUNRISE -> assertTrue(desc.contains("清晨") || desc.contains("日出"));
                case SUNSET -> assertTrue(desc.contains("黄昏") || desc.contains("日落"));
                case RAIN_START -> assertTrue(desc.contains("下雨"));
                case RAIN_END -> assertTrue(desc.contains("雨停"));
                case THUNDER_START -> assertTrue(desc.contains("雷暴") || desc.contains("打雷"));
                case THUNDER_END -> assertTrue(desc.contains("雷暴结束"));
                case BIOME_CHANGE -> assertTrue(desc.contains("来到"));
            }
        }
    }

    @Test
    public void testEventTypeEnumSize() {
        assertEquals(7, EnvironmentEventDetector.EventType.values().length);
    }

    // 反射调用 private getDayPhase
    private static int callGetDayPhase(long dayTime) {
        try {
            var method = EnvironmentEventDetector.class.getDeclaredMethod("getDayPhase", long.class);
            method.setAccessible(true);
            return (int) method.invoke(null, dayTime);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
