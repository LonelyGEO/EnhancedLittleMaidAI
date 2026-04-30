package com.github.lonelygeo.enhancedlittlemaidai.context;

import com.github.lonelygeo.enhancedlittlemaidai.compat.StorageCompat;
import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.IMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * 库存感知上下文 —— 查询女仆已查看过的库存内容并格式化为 LLM 可读文本。
 * 依赖 MSM 提供 ViewedInventoryMemory 数据，仅只读查询，不参与物品移动。
 */
public final class StorageContextProvider {

    private StorageContextProvider() {
    }

    public static IMaidContext createNearbyStorageContext() {
        return new IMaidContext() {
            @Override
            public String key() {
                return "nearby_storage";
            }

            @Override
            public String label() {
                return "Nearby Storage";
            }

            @Override
            public String getValue(EntityMaid maid) {
                if (!StorageCompat.isLoaded()) return "maid_storage_manager not loaded";

                try {
                    Map<Object, Object> positionMap = StorageCompat.getPositionFlattened(maid);
                    if (positionMap == null || positionMap.isEmpty()) {
                        return "No storage data recorded. The maid has not viewed any containers yet.";
                    }

                    int maxPositions = EnhancedConfig.STORAGE_MAX_POSITIONS.get();
                    int maxItemsPerPos = EnhancedConfig.STORAGE_MAX_ITEMS_PER_POS.get();
                    StringBuilder sb = new StringBuilder();
                    int posCount = 0;

                    for (Map.Entry<Object, Object> entry : positionMap.entrySet()) {
                        if (posCount >= maxPositions) break;
                        Object target = entry.getKey();
                        Object itemList = entry.getValue();

                        BlockPos pos = StorageCompat.getTargetPos(target);
                        if (pos == null) continue;

                        String posDesc = String.format("(%d,%d,%d)",
                                pos.getX(), pos.getY(), pos.getZ());
                        String targetType = StorageCompat.getTargetType(target);
                        if (targetType == null) targetType = "unknown";
                        sb.append(posDesc).append(" [").append(targetType).append("]: ");

                        if (itemList instanceof List<?> items) {
                            int itemCount = 0;
                            for (Object ic : items) {
                                if (itemCount >= maxItemsPerPos) {
                                    sb.append("...(more)");
                                    break;
                                }
                                try {
                                    ItemStack stack = (ItemStack) ic.getClass()
                                            .getMethod("item").invoke(ic);
                                    int count = (int) ic.getClass()
                                            .getMethod("count").invoke(ic);
                                    sb.append(Component.translatable(stack.getDescriptionId()).getString())
                                            .append(" x").append(count).append(", ");
                                } catch (Exception ignored) {
                                }
                                itemCount++;
                            }
                            if (itemCount > 0) {
                                sb.setLength(sb.length() - 2);
                            }
                        }
                        sb.append("; ");
                        posCount++;
                    }

                    String result = sb.isEmpty() ? "No items in known storage." : sb.toString();
                    if (EnhancedConfig.debugLog()) {
                        EnhancedLittleMaidAI.LOGGER.debug(
                                "EnhancedLittleMaidAI: nearby_storage returned {} positions",
                                posCount);
                    }
                    return result;
                } catch (Exception e) {
                    EnhancedLittleMaidAI.LOGGER.warn(
                            "EnhancedLittleMaidAI: nearby_storage context error: {}",
                            e.getMessage());
                    return "Error reading storage data.";
                }
            }
        };
    }

    public static IMaidContext createStorageSummaryContext() {
        return new IMaidContext() {
            @Override
            public String key() {
                return "storage_summary";
            }

            @Override
            public String label() {
                return "Storage Summary";
            }

            @Override
            public String getValue(EntityMaid maid) {
                if (!StorageCompat.isLoaded()) return "maid_storage_manager not loaded";

                try {
                    List<Object> flatList = StorageCompat.getFlattenedInventory(maid);
                    if (flatList == null || flatList.isEmpty()) {
                        return "No storage data. The maid has not viewed any containers yet.";
                    }

                    int maxItems = EnhancedConfig.STORAGE_MAX_ITEMS_SUMMARY.get();
                    int totalKinds = flatList.size();
                    int totalItems = 0;
                    int shownItems = 0;
                    StringBuilder sb = new StringBuilder();

                    for (Object invItem : flatList) {
                        ItemStack stack = StorageCompat.getInventoryItemStack(invItem);
                        int count = StorageCompat.getInventoryTotalCount(invItem);
                        totalItems += count;

                        if (shownItems < maxItems) {
                            if (stack != null) {
                                sb.append(Component.translatable(stack.getDescriptionId()).getString())
                                        .append(" x").append(count).append(", ");
                            }
                            shownItems++;
                        }
                    }

                    if (shownItems > 0) {
                        sb.setLength(sb.length() - 2);
                    }
                    if (totalKinds > maxItems) {
                        sb.append(" and ").append(totalKinds - maxItems)
                                .append(" more kinds");
                    }

                    String result = String.format("Total: %d kinds, %d items. %s",
                            totalKinds, totalItems, sb.toString());
                    if (EnhancedConfig.debugLog()) {
                        EnhancedLittleMaidAI.LOGGER.debug(
                                "EnhancedLittleMaidAI: storage_summary kinds={}, total={}",
                                totalKinds, totalItems);
                    }
                    return result;
                } catch (Exception e) {
                    EnhancedLittleMaidAI.LOGGER.warn(
                            "EnhancedLittleMaidAI: storage_summary context error: {}",
                            e.getMessage());
                    return "Error reading storage summary.";
                }
            }
        };
    }
}
