package com.github.lonelygeo.enhancedlittlemaidai.command;

import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryItem;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MindPalaceCommand {
    private MindPalaceCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("mindpalace")
            .requires(ctx -> ctx.hasPermission(2))
            .then(Commands.literal("list")
                .then(Commands.argument("maid", EntityArgument.entity())
                    .executes(ctx -> listMemories(ctx.getSource(), EntityArgument.getEntity(ctx, "maid")))))
            .then(Commands.literal("clear")
                .then(Commands.argument("maid", EntityArgument.entity())
                    .executes(ctx -> clearMemories(ctx.getSource(), EntityArgument.getEntity(ctx, "maid")))))
            .then(Commands.literal("stats")
                .then(Commands.argument("maid", EntityArgument.entity())
                    .executes(ctx -> showStats(ctx.getSource(), EntityArgument.getEntity(ctx, "maid")))));
    }

    private static int listMemories(CommandSourceStack src, net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof EntityMaid maid)) {
            src.sendFailure(Component.literal("Target is not a maid"));
            return 0;
        }
        MindPalace palace = MindPalace.get(maid.getUUID());
        if (palace == null || palace.size() == 0) {
            src.sendSuccess(() -> Component.literal("No memories for " + maid.getDisplayName().getString()), false);
            return 1;
        }
        List<MemoryItem> items = new java.util.ArrayList<>(palace.getStore().toList());
        items.sort(Comparator.comparingInt(MemoryItem::importance).reversed());
        for (MemoryItem m : items) {
            src.sendSuccess(() -> Component.literal(
                String.format("[%s|imp=%d] %s", m.category().name(), m.importance(), m.content())), false);
        }
        src.sendSuccess(() -> Component.literal("--- Total: " + items.size() + " memories ---"), false);
        return 1;
    }

    private static int clearMemories(CommandSourceStack src, net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof EntityMaid maid)) {
            src.sendFailure(Component.literal("Target is not a maid"));
            return 0;
        }
        MindPalace.remove(maid.getUUID());
        src.sendSuccess(() -> Component.literal("Cleared memories for " + maid.getDisplayName().getString()), false);
        return 1;
    }

    private static int showStats(CommandSourceStack src, net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof EntityMaid maid)) {
            src.sendFailure(Component.literal("Target is not a maid"));
            return 0;
        }
        MindPalace palace = MindPalace.get(maid.getUUID());
        if (palace == null || palace.size() == 0) {
            src.sendSuccess(() -> Component.literal("No memories for " + maid.getDisplayName().getString()), false);
            return 1;
        }
        var store = palace.getStore();
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        for (MemoryItem m : store.toList()) {
            categoryCounts.merge(m.category().name(), 1, Integer::sum);
        }
        src.sendSuccess(() -> Component.literal(
            String.format("Total: %d memories | %s", store.size(), categoryCounts)), false);
        return 1;
    }
}
