package com.github.lonelygeo.enhancedlittlemaidai.context;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.context.IMaidContext;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

/**
 * 新增上下文提供者集合。
 * 所有上下文注册到父模组已有类别中，无需新建类别。
 */
public final class ContextProviders {

    private static final int PLAYER_SCAN_RADIUS = 32;
    private static final int MAX_NEARBY_PLAYERS = 5;

    private ContextProviders() {
    }

    /**
     * 饥饿值上下文 — 注册到 status 类别。
     * getValue 返回: "hunger=18/20"
     */
    public static IMaidContext createFoodContext() {
        return new IMaidContext() {
            @Override public String key() { return "food"; }
            @Override public String label() { return "Food Level"; }

            @Override
            public String getValue(EntityMaid maid) {
                return "hunger=" + maid.getHunger() + "/20";
            }
        };
    }

    /**
     * 工具耐久上下文 — 注册到 equipment 类别。
     * getValue 返回: "netherite_pickaxe durability=1234/2031"
     * 空手时返回: "none"
     */
    public static IMaidContext createToolDurabilityContext() {
        return new IMaidContext() {
            @Override public String key() { return "tool_durability"; }
            @Override public String label() { return "Tool Durability"; }

            @Override
            public String getValue(EntityMaid maid) {
                ItemStack mainHand = maid.getMainHandItem();
                if (mainHand.isEmpty()) return "none";

                int maxDmg = mainHand.getMaxDamage();
                if (maxDmg <= 0) return mainHand.getDescriptionId();

                int dmg = mainHand.getDamageValue();
                return mainHand.getDescriptionId()
                        + " durability=" + (maxDmg - dmg) + "/" + maxDmg;
            }
        };
    }

    /**
     * 附近玩家上下文 — 注册到 nearby_entities 类别。
     * getValue 返回: "Player1(dist=3), Player2(dist=12)"
     * 无其他玩家时返回: "none"
     */
    public static IMaidContext createNearbyPlayersContext() {
        return new IMaidContext() {
            @Override public String key() { return "nearby_players"; }
            @Override public String label() { return "Nearby Players"; }

            @Override
            public String getValue(EntityMaid maid) {
                Level level = maid.level();
                AABB range = maid.getBoundingBox().inflate(PLAYER_SCAN_RADIUS);
                List<ServerPlayer> players = level.getEntitiesOfClass(
                        ServerPlayer.class, range,
                        p -> p.isAlive() && p != maid.getOwner()
                );
                if (players.isEmpty()) return "none";

                players.sort(Comparator.comparingDouble(p -> p.distanceToSqr(maid)));
                if (players.size() > MAX_NEARBY_PLAYERS) {
                    players = players.subList(0, MAX_NEARBY_PLAYERS);
                }

                StringBuilder sb = new StringBuilder();
                for (Player p : players) {
                    sb.append(p.getDisplayName().getString())
                            .append("(dist=").append((int) p.distanceTo(maid)).append("), ");
                }
                return sb.substring(0, sb.length() - 2);
            }
        };
    }
}
