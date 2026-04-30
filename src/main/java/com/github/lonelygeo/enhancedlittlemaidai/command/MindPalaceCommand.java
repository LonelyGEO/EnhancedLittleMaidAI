package com.github.lonelygeo.enhancedlittlemaidai.command;

import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryItem;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.lonelygeo.enhancedlittlemaidai.util.bm25.Bm25Index;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class MindPalaceCommand {
    private MindPalaceCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("mindpalace")
            .then(Commands.literal("list")
                .then(Commands.argument("maid", EntityArgument.entities())
                    .executes(ctx -> listMemories(ctx.getSource(),
                            EntityArgument.getEntities(ctx, "maid")))))
            .then(Commands.literal("clear")
                .then(Commands.argument("maid", EntityArgument.entities())
                    .executes(ctx -> clearMemories(ctx.getSource(),
                            EntityArgument.getEntities(ctx, "maid")))))
            .then(Commands.literal("stats")
                .then(Commands.argument("maid", EntityArgument.entities())
                    .executes(ctx -> showStats(ctx.getSource(),
                            EntityArgument.getEntities(ctx, "maid")))))
            .then(Commands.literal("search")
                .then(Commands.argument("maid", EntityArgument.entities())
                    .then(Commands.argument("keyword", StringArgumentType.word())
                        .executes(ctx -> searchMemories(ctx.getSource(),
                                EntityArgument.getEntities(ctx, "maid"),
                                StringArgumentType.getString(ctx, "keyword"))))));
    }

    private static EntityMaid getFirstMaid(CommandSourceStack src, Collection<? extends net.minecraft.world.entity.Entity> entities) {
        for (var e : entities) {
            if (e instanceof EntityMaid maid) return maid;
        }
        return null;
    }

    private static int listMemories(CommandSourceStack src, Collection<? extends net.minecraft.world.entity.Entity> entities) {
        EntityMaid maid = getFirstMaid(src, entities);
        if (maid == null) {
            src.sendFailure(Component.literal("目标不是女仆"));
            return 0;
        }
        MindPalace palace = MindPalace.get(maid.getUUID());
        if (palace == null || palace.size() == 0) {
            src.sendSuccess(() -> Component.literal(
                    maid.getDisplayName().getString() + " 没有记忆"), false);
            return 1;
        }

        List<MemoryItem> items = new java.util.ArrayList<>(palace.getStore().toList());
        items.sort(Comparator.comparingInt(MemoryItem::importance)
                .thenComparing(Comparator.comparingLong(MemoryItem::gameTime).reversed()));

        String header = "--- " + maid.getDisplayName().getString()
                + " 的记忆 (共 " + items.size() + " 条) ---";
        src.sendSuccess(() -> Component.literal(header), false);

        int index = 1;
        for (MemoryItem m : items) {
            StringBuilder line = new StringBuilder();
            line.append("#").append(index++).append(" [")
                    .append(categoryName(m.category())).append("] ")
                    .append(m.content());

            StringBuilder meta = new StringBuilder();
            if (m.location().isPresent() || m.dimension().isPresent()) {
                meta.append("  (");
                m.location().ifPresent(loc ->
                        meta.append(loc.getX()).append(",")
                                .append(loc.getY()).append(",")
                                .append(loc.getZ()));
                m.dimension().ifPresent(dim ->
                        meta.append(" ").append(dim));
                meta.append(")");
            }
            meta.append(" | 重要度:").append(m.importance());
            meta.append(" | 查阅:").append(m.accessCount()).append("次");

            src.sendSuccess(() -> Component.literal(line.toString()), false);
            src.sendSuccess(() -> Component.literal(meta.toString()), false);
        }
        return 1;
    }

    private static int searchMemories(CommandSourceStack src,
                                        Collection<? extends net.minecraft.world.entity.Entity> entities,
                                        String keyword) {
        EntityMaid maid = getFirstMaid(src, entities);
        if (maid == null) {
            src.sendFailure(Component.literal("目标不是女仆"));
            return 0;
        }
        MindPalace palace = MindPalace.get(maid.getUUID());
        if (palace == null || palace.size() == 0) {
            src.sendSuccess(() -> Component.literal(
                    maid.getDisplayName().getString() + " 没有记忆"), false);
            return 1;
        }

        List<MemoryItem> items = palace.getStore().retrieve(keyword, 5);
        if (items.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                    maid.getDisplayName().getString() + " 搜索 \"" + keyword + "\": 无结果"), false);
            return 1;
        }

        String header = maid.getDisplayName().getString() + " 搜索 \"" + keyword
                + "\" 结果 (前 " + items.size() + " 条):";
        src.sendSuccess(() -> Component.literal(header), false);

        for (MemoryItem m : items) {
            StringBuilder line = new StringBuilder();
            line.append("  [").append(categoryName(m.category())).append("] ")
                    .append(m.content());
            m.location().ifPresent(loc ->
                    line.append(" (").append(loc.getX()).append(",")
                            .append(loc.getY()).append(",")
                            .append(loc.getZ()).append(")"));

            src.sendSuccess(() -> Component.literal(line.toString()), false);
        }
        return 1;
    }

    private static int clearMemories(CommandSourceStack src, Collection<? extends net.minecraft.world.entity.Entity> entities) {
        EntityMaid maid = getFirstMaid(src, entities);
        if (maid == null) {
            src.sendFailure(Component.literal("目标不是女仆"));
            return 0;
        }
        MindPalace palace = MindPalace.get(maid.getUUID());
        int count = palace != null ? palace.size() : 0;
        MindPalace.remove(maid.getUUID());
        String msg = count > 0
                ? "已清除 " + maid.getDisplayName().getString() + " 的 " + count + " 条记忆"
                : maid.getDisplayName().getString() + " 没有记忆";
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int showStats(CommandSourceStack src, Collection<? extends net.minecraft.world.entity.Entity> entities) {
        EntityMaid maid = getFirstMaid(src, entities);
        if (maid == null) {
            src.sendFailure(Component.literal("目标不是女仆"));
            return 0;
        }
        MindPalace palace = MindPalace.get(maid.getUUID());
        if (palace == null || palace.size() == 0) {
            src.sendSuccess(() -> Component.literal(
                    maid.getDisplayName().getString() + " 没有记忆"), false);
            return 1;
        }
        var store = palace.getStore();
        int place = 0, person = 0, event = 0, pref = 0, knowledge = 0;
        for (MemoryItem m : store.toList()) {
            switch (m.category()) {
                case PLACE -> place++;
                case PERSON -> person++;
                case EVENT -> event++;
                case PREFERENCE -> pref++;
                case KNOWLEDGE -> knowledge++;
            }
        }

        String result = maid.getDisplayName().getString() + " 记忆统计\n"
                + "共 " + store.size() + " 条"
                + " | 地点:" + place
                + " | 人物:" + person
                + " | 事件:" + event
                + " | 偏好:" + pref
                + " | 知识:" + knowledge;

        src.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static String categoryName(com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryCategory cat) {
        return switch (cat) {
            case PLACE -> "地点";
            case PERSON -> "人物";
            case EVENT -> "事件";
            case PREFERENCE -> "偏好";
            case KNOWLEDGE -> "知识";
        };
    }
}
