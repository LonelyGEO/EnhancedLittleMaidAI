package com.github.lonelygeo.enhancedlittlemaidai.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * MiningLittleMaid 运行时兼容层。
 * 所有 Mining 侧 API 调用通过反射实现，确保 Mining 未安装时不会触发 NoClassDefFoundError。
 */
public final class MiningCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean LOADED = ModList.get().isLoaded("mining_little_maid");

    private MiningCompat() {}

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean isMineableOre(BlockState state) {
        return invokeStatic("com.github.lonelygeo.mininglittlemaid.task.MiningFavorGate",
                "isMineableOre", false, state);
    }

    public static int getSniffRadius(int favorLevel) {
        return invokeStatic("com.github.lonelygeo.mininglittlemaid.task.MiningFavorGate",
                "getSniffRadius", 1, favorLevel);
    }

    public static boolean canMineAtLevel(BlockState state, int favorLevel) {
        return invokeStatic("com.github.lonelygeo.mininglittlemaid.task.MiningFavorGate",
                "canMineAtLevel", false, state, favorLevel);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeStatic(String className, String methodName, T defaultValue, Object... args) {
        try {
            Class<?> clazz = Class.forName(className);
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i].getClass();
                if (paramTypes[i] == Integer.class) paramTypes[i] = int.class;
                if (paramTypes[i] == Boolean.class) paramTypes[i] = boolean.class;
            }
            return (T) clazz.getMethod(methodName, paramTypes).invoke(null, args);
        } catch (Exception e) {
            LOGGER.warn("Failed to invoke {}.{}: {}", className, methodName, e.getMessage());
            return defaultValue;
        }
    }
}
