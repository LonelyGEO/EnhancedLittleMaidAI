package com.github.lonelygeo.enhancedlittlemaidai;

import com.github.lonelygeo.enhancedlittlemaidai.compat.MiningCompat;
import com.github.lonelygeo.enhancedlittlemaidai.context.BlockAwareContexts;
import com.github.lonelygeo.enhancedlittlemaidai.context.MiningContextProvider;
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
        register.registerCategory("entity_details",
                "Detailed info of nearby entities (HP, profession, hostility, etc.)",
                false);
        register.registerCategory("environment_detail",
                "Light level, indoor/outdoor status, and redstone power",
                false);

        BlockAwareContexts.registerAll(register);

        if (MiningCompat.isLoaded()) {
            register.registerCategory("mining_info",
                    "Mining-specific info: nearby ores and maid mining status",
                    false);
            register.registerContext("mining_info", MiningContextProvider.createNearbyOresContext());
            register.registerContext("mining_info", MiningContextProvider.createMiningStatusContext());
        }
    }
}
