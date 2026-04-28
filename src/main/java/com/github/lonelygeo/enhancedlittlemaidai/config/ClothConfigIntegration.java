package com.github.lonelygeo.enhancedlittlemaidai.config;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import net.neoforged.fml.ModList;

/**
 * Cloth Config 设置界面集成入口。
 * 仅在 cloth_config 已加载时通过反射实例化 ClothConfigHandler，
 * 避免直接引用 Cloth Config API 类导致 NoClassDefFoundError。
 */
public final class ClothConfigIntegration {

    private ClothConfigIntegration() {
    }

    /**
     * 在模组构造器中调用。若 Cloth Config 未加载则不做任何事。
     */
    public static void registerIfAvailable() {
        if (!ModList.get().isLoaded("cloth_config")) return;
        try {
            Class<?> handlerClass = Class.forName(
                    "com.github.lonelygeo.enhancedlittlemaidai.config.ClothConfigHandler");
            handlerClass.getMethod("register").invoke(null);
            EnhancedLittleMaidAI.LOGGER.info("EnhancedLittleMaidAI: Cloth Config integration registered");
        } catch (Exception e) {
            EnhancedLittleMaidAI.LOGGER.warn(
                    "EnhancedLittleMaidAI: Failed to register Cloth Config integration", e);
        }
    }
}
