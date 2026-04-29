package com.github.lonelygeo.enhancedlittlemaidai.compat;

import com.mojang.logging.LogUtils;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * MaidStorageManager 运行时兼容层。
 * 所有 MSM 侧 API 调用通过反射实现，确保 MSM 未安装时不会触发 NoClassDefFoundError。
 *
 * <pre>{@code
 * if (StorageCompat.isLoaded()) {
 *     List<?> items = StorageCompat.getFlattenedInventory(maid);
 *     int count = StorageCompat.getItemCount(maid, diamondStack);
 * }
 * }</pre>
 */
public final class StorageCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean LOADED = ModList.get().isLoaded("maid_storage_manager");

    private StorageCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static List<Object> getFlattenedInventory(EntityMaid maid) {
        if (maid == null) return null;
        try {
            Class<?> memUtilClass = Class.forName(
                    "studio.fantasyit.maid_storage_manager.util.MemoryUtil");
            Object viewedInv = memUtilClass.getMethod("getViewedInventory",
                    Object.class).invoke(null, maid);
            if (viewedInv == null) return null;
            return (List<Object>) viewedInv.getClass().getMethod("flatten").invoke(viewedInv);
        } catch (Exception e) {
            LOGGER.warn("StorageCompat getFlattenedInventory failed: {}", e.getMessage());
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static Map<Object, Object> getPositionFlattened(EntityMaid maid) {
        if (maid == null) return null;
        try {
            Class<?> memUtilClass = Class.forName(
                    "studio.fantasyit.maid_storage_manager.util.MemoryUtil");
            Object viewedInv = memUtilClass.getMethod("getViewedInventory",
                    Object.class).invoke(null, maid);
            if (viewedInv == null) return null;
            return (Map<Object, Object>) viewedInv.getClass()
                    .getMethod("positionFlatten").invoke(viewedInv);
        } catch (Exception e) {
            LOGGER.warn("StorageCompat getPositionFlattened failed: {}", e.getMessage());
            return null;
        }
    }

    public static int getItemCount(EntityMaid maid, ItemStack itemStack) {
        if (maid == null || itemStack == null) return -1;
        try {
            Class<?> memUtilClass = Class.forName(
                    "studio.fantasyit.maid_storage_manager.util.MemoryUtil");
            Object viewedInv = memUtilClass.getMethod("getViewedInventory",
                    Object.class).invoke(null, maid);
            if (viewedInv == null) return -1;

            Class<?> matchTypeEnum = Class.forName(
                    "studio.fantasyit.maid_storage_manager.util.ItemStackUtil$MATCH_TYPE");
            Object sameItem = matchTypeEnum.getField("SAME_ITEM").get(null);

            return (int) viewedInv.getClass()
                    .getMethod("getItemCount", ItemStack.class, matchTypeEnum)
                    .invoke(viewedInv, itemStack, sameItem);
        } catch (Exception e) {
            LOGGER.warn("StorageCompat getItemCount failed: {}", e.getMessage());
            return -1;
        }
    }

    @Nullable
    public static BlockPos getTargetPos(Object target) {
        if (target == null) return null;
        try {
            return (BlockPos) target.getClass().getField("pos").get(target);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static String getTargetType(Object target) {
        if (target == null) return null;
        try {
            return target.getClass().getField("type").get(target).toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static ItemStack getInventoryItemStack(Object inventoryItem) {
        if (inventoryItem == null) return null;
        try {
            return (ItemStack) inventoryItem.getClass().getField("itemStack").get(inventoryItem);
        } catch (Exception e) {
            return null;
        }
    }

    public static int getInventoryTotalCount(Object inventoryItem) {
        if (inventoryItem == null) return 0;
        try {
            return (int) inventoryItem.getClass().getField("totalCount").get(inventoryItem);
        } catch (Exception e) {
            return 0;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static List<Object> getInventoryPositionCounts(Object inventoryItem) {
        if (inventoryItem == null) return null;
        try {
            return (List<Object>) inventoryItem.getClass().getField("posAndSlot")
                    .get(inventoryItem);
        } catch (Exception e) {
            return null;
        }
    }
}
