package com.github.lonelygeo.enhancedlittlemaidai.compat;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryCategory;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryItem;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Optional;
import java.util.UUID;

/**
 * 监听 MSM 仓储管理事件，将储物操作写入 ELMAI 思维宫殿记忆。
 * 仅在 MSM 加载时由 EnhancedLittleMaidAI 反射注册。
 */
public final class StorageMemoryHandler {

    private StorageMemoryHandler() {
    }

    /**
     * 注册到 NeoForge 事件总线。由 EnhancedLittleMaidAI 反射调用。
     */
    public static void register() {
        NeoForge.EVENT_BUS.register(StorageMemoryHandler.class);
        EnhancedLittleMaidAI.LOGGER.info("EnhancedLittleMaidAI: StorageMemoryHandler registered");
    }

    /**
     * 监听 RequestListStatusChangeEvent。Status.END 时写入 MindPalace 记忆。
     * 事件 class: studio.fantasyit.maid_storage_manager.api.event.RequestListStatusChangeEvent
     */
    @SubscribeEvent
    public static void onRequestListStatusChange(Object event) {
        if (!EnhancedConfig.ENABLE_STORAGE_MEMORY.get()) return;

        try {
            String statusName = ((Enum<?>) event.getClass().getMethod("getStatus")
                    .invoke(event)).name();
            if (!"END".equals(statusName)) return;

            EntityMaid maid = (EntityMaid) event.getClass().getMethod("getMaid")
                    .invoke(event);
            if (maid == null || maid.isRemoved()) return;

            MindPalace palace = MindPalace.getOrCreate(maid.getUUID());
            BlockPos maidPos = maid.blockPosition();
            String dimension = maid.level().dimension().location().toString();
            long gameTime = maid.level().getGameTime();

            String itemSummary = extractItemSummary(event);
            String content = "从存储中取出了: " + itemSummary;

            MemoryItem memory = new MemoryItem(
                    UUID.randomUUID(),
                    MemoryCategory.EVENT,
                    content,
                    Optional.of(maidPos),
                    Optional.of(dimension),
                    gameTime,
                    0,
                    3
            );

            palace.getStore().add(memory, gameTime);

            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.info(
                        "EnhancedLittleMaidAI: Storage memory recorded for maid {}: {}",
                        maid.getUUID(),
                        content.substring(0, Math.min(50, content.length())));
            }
        } catch (Exception e) {
            EnhancedLittleMaidAI.LOGGER.warn(
                    "EnhancedLittleMaidAI: StorageMemoryHandler error: {}", e.getMessage());
        }
    }

    private static String extractItemSummary(Object event) {
        try {
            Object itemStackObj = event.getClass().getMethod("getItemStack").invoke(event);
            if (itemStackObj == null) return "物品";

            // 尝试 RequestListItem.getRequestItems 获取详细列表
            try {
                Class<?> rliClass = Class.forName(
                        "studio.fantasyit.maid_storage_manager.items.RequestListItem");
                Object requestListObj = rliClass.getMethod("getRequestItems",
                        Object.class).invoke(null, itemStackObj);
                if (requestListObj != null) {
                    String display = (String) requestListObj.getClass()
                            .getMethod("display").invoke(requestListObj);
                    if (display != null && display.length() > 60) {
                        display = display.substring(0, 57) + "...";
                    }
                    return display != null ? display : "物品";
                }
            } catch (Exception ignored) {
            }

            // 回退：ItemStack 描述名
            return (String) itemStackObj.getClass()
                    .getMethod("getDescriptionId").invoke(itemStackObj);
        } catch (Exception e) {
            return "物品";
        }
    }
}
