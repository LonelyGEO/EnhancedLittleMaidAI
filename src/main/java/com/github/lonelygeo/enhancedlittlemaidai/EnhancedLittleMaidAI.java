package com.github.lonelygeo.enhancedlittlemaidai;

import com.github.lonelygeo.enhancedlittlemaidai.command.MindPalaceCommand;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(EnhancedLittleMaidAI.MOD_ID)
public class EnhancedLittleMaidAI {
    public static final String MOD_ID = "enhanced_little_maid_ai";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 调试日志开关。通过 JVM 参数或反射修改，无需配置文件。 */
    public static boolean DEBUG_LOG = false;

    public EnhancedLittleMaidAI(IEventBus modEventBus) {
        LOGGER.info("Enhanced Little Maid AI addon loaded. Thinking/reasoning content support enabled.");
        modEventBus.addListener(this::registerCommands);
    }

    @SubscribeEvent
    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(MindPalaceCommand.register());
    }
}
