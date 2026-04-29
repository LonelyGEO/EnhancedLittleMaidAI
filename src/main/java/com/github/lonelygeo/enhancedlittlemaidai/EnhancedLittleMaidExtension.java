package com.github.lonelygeo.enhancedlittlemaidai;

import com.github.lonelygeo.enhancedlittlemaidai.compat.MiningCompat;
import com.github.lonelygeo.enhancedlittlemaidai.compat.StorageCompat;
import com.github.lonelygeo.enhancedlittlemaidai.context.BlockAwareContexts;
import com.github.lonelygeo.enhancedlittlemaidai.context.MiningContextProvider;
import com.github.lonelygeo.enhancedlittlemaidai.context.ContextProviders;
import com.github.lonelygeo.enhancedlittlemaidai.context.StorageContextProvider;
import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
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

        register.registerContext("status", ContextProviders.createFoodContext());
        register.registerContext("equipment", ContextProviders.createToolDurabilityContext());
        register.registerContext("nearby_entities", ContextProviders.createNearbyPlayersContext());

        if (MiningCompat.isLoaded()) {
            register.registerCategory("mining_info",
                    "Mining-specific info: nearby ores and maid mining status",
                    false);
            register.registerContext("mining_info", MiningContextProvider.createNearbyOresContext());
            register.registerContext("mining_info", MiningContextProvider.createMiningStatusContext());
        }

        if (StorageCompat.isLoaded()) {
            register.registerCategory("inventory",
                    "Storage inventory info: nearby containers and their contents",
                    false);
            register.registerContext("inventory",
                    StorageContextProvider.createNearbyStorageContext());
            register.registerContext("inventory",
                    StorageContextProvider.createStorageSummaryContext());
        }

        EnhancedLittleMaidAI.LOGGER.info(
                "EnhancedLittleMaidAI: Registered context categories — nearby_blocks, environment_detail, nearby_entities{}{}",
                MiningCompat.isLoaded() ? ", mining_info" : "",
                StorageCompat.isLoaded() ? ", inventory" : "");
    }
}
