package com.github.lonelygeo.enhancedlittlemaidai;

import com.github.lonelygeo.enhancedlittlemaidai.command.MindPalaceCommand;
import com.github.lonelygeo.enhancedlittlemaidai.compat.MiningCompat;
import com.github.lonelygeo.enhancedlittlemaidai.config.ClothConfigIntegration;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(EnhancedLittleMaidAI.MOD_ID)
public class EnhancedLittleMaidAI {
    public static final String MOD_ID = "enhancedlittlemaidai";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EnhancedLittleMaidAI(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Enhanced Little Maid AI addon loaded.");
        modContainer.registerConfig(ModConfig.Type.COMMON, EnhancedConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        ClothConfigIntegration.registerIfAvailable();
        registerMiningMessageHandlerIfAvailable();
    }

    @SubscribeEvent
    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(MindPalaceCommand.register());
    }

    private static void registerMiningMessageHandlerIfAvailable() {
        if (!MiningCompat.isLoaded()) return;
        try {
            Class<?> handlerClass = Class.forName(
                    "com.github.lonelygeo.enhancedlittlemaidai.compat.MiningMessageHandler");
            handlerClass.getMethod("register").invoke(null);
        } catch (Exception e) {
            LOGGER.debug("EnhancedLittleMaidAI: MiningMessageHandler not available");
        }
    }
}
