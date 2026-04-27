package com.github.lonelygeo.enhancedlittlemaidai;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(EnhancedLittleMaidAI.MOD_ID)
public class EnhancedLittleMaidAI {
    public static final String MOD_ID = "enhanced_little_maid_ai";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EnhancedLittleMaidAI(IEventBus modEventBus) {
        LOGGER.info("Enhanced Little Maid AI addon loaded. Thinking/reasoning content support enabled.");
    }
}
