package com.github.lonelygeo.enhancedlittlemaidai.context;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.GameContextRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.IMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.util.*;

public final class BlockAwareContexts {
    private static final int BFS_MAX_DEPTH = 5;
    private static final int ENTITY_RADIUS = 16;
    private static final int MAX_ENTITIES = 30;

    public static void registerAll(GameContextRegister register) {
        register.registerContext("nearby_blocks", new NearbyBlocksContext());
        register.registerContext("environment_detail", new EnvironmentDetailContext());
    }

    public static IMaidContext createEntityDetailContext() {
        return new EntityDetailContext();
    }

    private static class NearbyBlocksContext implements IMaidContext {
        @Override
        public String key() {
            return "nearby_blocks";
        }

        @Override
        public String label() {
            return "Nearby Blocks";
        }

        @Override
        public String getValue(EntityMaid maid) {
            Level level = maid.level();
            BlockPos center = maid.blockPosition();
            Map<Block, Integer> counts = new LinkedHashMap<>();

            Set<BlockPos> visited = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(center);
            visited.add(center);

            int depth = 0;
            while (!queue.isEmpty() && depth <= BFS_MAX_DEPTH) {
                int layerSize = queue.size();
                for (int i = 0; i < layerSize; i++) {
                    BlockPos pos = queue.poll();
                    Block block = level.getBlockState(pos).getBlock();
                    counts.merge(block, 1, Integer::sum);
                    for (BlockPos next : sampleDirections(pos)) {
                        if (visited.add(next)) {
                            queue.add(next);
                        }
                    }
                }
                depth++;
            }

            StringBuilder sb = new StringBuilder();
            counts.entrySet().stream()
                    .sorted(Map.Entry.<Block, Integer>comparingByValue().reversed())
                    .limit(15)
                    .forEach(e -> sb.append(e.getKey().getDescriptionId())
                            .append(" x").append(e.getValue()).append("; "));
            String result = sb.isEmpty() ? "none" : sb.toString();
            if (EnhancedLittleMaidAI.DEBUG_LOG) {
                EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: nearby_blocks BFS result len={}", result.length());
            }
            return result;
        }

        private static BlockPos[] sampleDirections(BlockPos center) {
            return new BlockPos[]{
                    center.north(), center.south(), center.east(), center.west(),
                    center.above(), center.below()
            };
        }
    }

    private static class EnvironmentDetailContext implements IMaidContext {
        @Override
        public String key() {
            return "environment_detail";
        }

        @Override
        public String label() {
            return "Environment Detail";
        }

        @Override
        public String getValue(EntityMaid maid) {
            BlockPos pos = maid.blockPosition();
            Level level = maid.level();
            int light = level.getMaxLocalRawBrightness(pos);
            boolean skyVisible = level.canSeeSky(pos);
            boolean indoors = !skyVisible && light < 8;
            boolean hasCeiling = level.getBlockState(pos.above(3)).isSolid();
            int redstone = level.getBestNeighborSignal(pos);

            String result = String.format("light=%d, indoors=%b, sky_visible=%b, redstone_power=%d",
                    light, indoors || hasCeiling, skyVisible, redstone);
            if (EnhancedLittleMaidAI.DEBUG_LOG) {
                EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: environment_detail result={}", result);
            }
            return result;
        }
    }

    private static class EntityDetailContext implements IMaidContext {
        @Override
        public String key() {
            return "nearby_entities_detail";
        }

        @Override
        public String label() {
            return "Nearby Entity Details";
        }

        @Override
        public String getValue(EntityMaid maid) {
            Level level = maid.level();
            AABB range = maid.getBoundingBox().inflate(ENTITY_RADIUS);
            List<LivingEntity> entities = level.getEntitiesOfClass(
                    LivingEntity.class, range,
                    e -> e != maid && e.isAlive()
            );
            entities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(maid)));
            if (entities.size() > MAX_ENTITIES) {
                entities = entities.subList(0, MAX_ENTITIES);
            }

            StringBuilder sb = new StringBuilder();
            for (LivingEntity e : entities) {
                sb.append(e.getDisplayName().getString());
                sb.append("(hp=").append((int) e.getHealth()).append("/").append((int) e.getMaxHealth()).append(")");
                sb.append(", dist=").append((int) e.distanceTo(maid));
                if (e instanceof Villager v) {
                    sb.append(", profession=").append(v.getVariant().toString());
                }
                if (e instanceof TamableAnimal t) {
                    sb.append(t.isTame() ? ", tamed" : "");
                    sb.append(t.isAggressive() ? ", aggressive" : "");
                }
                if (e instanceof Mob m && m.getTarget() != null) {
                    sb.append(", targeting=").append(m.getTarget().getDisplayName().getString());
                }
                sb.append("; ");
            }
            String result = sb.isEmpty() ? "none" : sb.toString();
            if (EnhancedLittleMaidAI.DEBUG_LOG) {
                EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: entity_detail entities={}", entities.size());
            }
            return result;
        }
    }
}
