package com.github.lonelygeo.enhancedlittlemaidai.compat;

import net.neoforged.fml.ModList;

/**
 * MaidStorageManager 运行时兼容层。
 * 所有 MSM 侧 API 调用通过反射实现，确保 MSM 未安装时不会触发 NoClassDefFoundError。
 */
public final class StorageCompat {
    private static final String MOD_ID = "maidstoragemanager";
    private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);

    private StorageCompat() {}

    public static boolean isLoaded() {
        return LOADED;
    }
}
