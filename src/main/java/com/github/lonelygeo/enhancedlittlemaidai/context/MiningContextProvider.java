package com.github.lonelygeo.enhancedlittlemaidai.context;

import com.github.lonelygeo.enhancedlittlemaidai.compat.MiningCompat;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.IMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 采矿环境上下文 —— 扫描女仆周围矿石并格式化为 LLM 可读文本。
 * 扫描半径由好感度等级决定（通过 MiningCompat.getSniffRadius 获取）。
 */
public final class MiningContextProvider {

    private MiningContextProvider() {}

    public static IMaidContext createNearbyOresContext() {
        return new IMaidContext() {
            @Override
            public String key() {
                return "mining_nearby_ores";
            }

            @Override
            public String label() {
                return "Nearby Ores";
            }

            @Override
            public String getValue(EntityMaid maid) {
                if (!MiningCompat.isLoaded()) return "mining_little_maid not loaded";

                Level level = maid.level();
                BlockPos center = maid.blockPosition();
                int favorLevel = maid.getFavorabilityManager().getLevel();
                int sniffRadius = MiningCompat.getSniffRadius(favorLevel);

                Map<Block, Integer> oreCounts = new LinkedHashMap<>();

                for (int x = -sniffRadius; x <= sniffRadius; x++) {
                    for (int y = -sniffRadius; y <= sniffRadius; y++) {
                        for (int z = -sniffRadius; z <= sniffRadius; z++) {
                            BlockPos pos = center.offset(x, y, z);
                            BlockState state = level.getBlockState(pos);
                            if (MiningCompat.isMineableOre(state)) {
                                oreCounts.merge(state.getBlock(), 1, Integer::sum);
                            }
                        }
                    }
                }

                if (oreCounts.isEmpty()) {
                    return "No mineable ores detected within sniff range (radius=" + sniffRadius + ")";
                }

                StringBuilder sb = new StringBuilder();
                oreCounts.entrySet().stream()
                        .sorted(Map.Entry.<Block, Integer>comparingByValue().reversed())
                        .forEach(e -> sb.append(e.getKey().getDescriptionId())
                                .append(" x").append(e.getValue()).append("; "));
                sb.append("| sniff_radius=").append(sniffRadius);
                String result = sb.toString();
                if (TouhouLittleMaid.DEBUG) {
                    TouhouLittleMaid.LOGGER.debug("EnhancedLittleMaidAI: mining_nearby_ores radius={}, ores={}",
                            sniffRadius, oreCounts.size());
                }
                return result;
            }
        };
    }

    public static IMaidContext createMiningStatusContext() {
        return new IMaidContext() {
            @Override
            public String key() {
                return "mining_status";
            }

            @Override
            public String label() {
                return "Mining Status";
            }

            @Override
            public String getValue(EntityMaid maid) {
                if (!MiningCompat.isLoaded()) return "mining_little_maid not loaded";

                int favorLevel = maid.getFavorabilityManager().getLevel();
                int sniffRadius = MiningCompat.getSniffRadius(favorLevel);

                boolean isActive = false;
                if (maid.getTask() != null) {
                    ResourceLocation uid = maid.getTask().getUid();
                    isActive = "mining_little_maid".equals(uid.getNamespace())
                            && "mining".equals(uid.getPath());
                }

                String result = String.format("active=%b, favor_level=%d, sniff_radius=%d",
                        isActive, favorLevel, sniffRadius);
                if (TouhouLittleMaid.DEBUG) {
                    TouhouLittleMaid.LOGGER.debug("EnhancedLittleMaidAI: mining_status active={}", isActive);
                }
                return result;
            }
        };
    }
}
