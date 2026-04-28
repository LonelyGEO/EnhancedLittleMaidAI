package com.github.lonelygeo.enhancedlittlemaidai.config;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;

/**
 * Cloth Config 设置界面集成入口。
 * 通过反射加载 ClothConfigHandler 避免直接引用 Cloth Config API 导致 NoClassDefFoundError。
 * 当 TLM 的 AddClothConfigEvent 触发时，Handler 将配置项注入 TLM 配置菜单。
 */
public final class ClothConfigIntegration {

    private ClothConfigIntegration() {
    }

    /**
     * 在模组构造器中调用。尝试反射注册 ClothConfigHandler 到 NeoForge.EVENT_BUS。
     * 若 Cloth Config 类不可用（生产环境无对应 jar），Class.forName 会失败，静默跳过。
     */
    public static void registerIfAvailable() {
        try {
            Class<?> handlerClass = Class.forName(
                    "com.github.lonelygeo.enhancedlittlemaidai.config.ClothConfigHandler");
            handlerClass.getMethod("register").invoke(null);
            EnhancedLittleMaidAI.LOGGER.info("EnhancedLittleMaidAI: Cloth Config integration registered");
        } catch (Exception e) {
            EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: Cloth Config not available, GUI config skipped");
        }
    }
}
