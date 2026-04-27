package com.github.lonelygeo.enhancedlittlemaidai;

import com.github.lonelygeo.enhancedlittlemaidai.compat.MiningCompat;
import com.github.lonelygeo.enhancedlittlemaidai.context.BlockAwareContexts;
import com.github.lonelygeo.enhancedlittlemaidai.context.MiningContextProvider;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;

@LittleMaidExtension
public class EnhancedLittleMaidExtension implements ILittleMaid {

    public EnhancedLittleMaidExtension() {
    }

    @Override
    public void registerAIMaidContext(GameContextRegister register) {
        register.registerCategory("nearby_blocks",
                "Block types around the maid (BFS depth 5 sampling)",
                false);
        register.registerCategory("environment_detail",
                "Light level, indoor/outdoor status, and redstone power",
                false);

        BlockAwareContexts.registerAll(register);

        register.registerContext("nearby_entities",
                BlockAwareContexts.createEntityDetailContext());

        if (MiningCompat.isLoaded()) {
            register.registerCategory("mining_info",
                    "Mining-specific info: nearby ores and maid mining status",
                    false);
            register.registerContext("mining_info", MiningContextProvider.createNearbyOresContext());
            register.registerContext("mining_info", MiningContextProvider.createMiningStatusContext());
        }

        TouhouLittleMaid.LOGGER.info(
                "EnhancedLittleMaidAI: Registered context categories — nearby_blocks, environment_detail, nearby_entities{}",
                MiningCompat.isLoaded() ? ", mining_info" : "");
    }
}
