package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;

/**
 * 聊天栏 /maid 命令。
 * 格式: /maid <消息> 或 /maid <名字> <消息>
 * 匹配最近的女仆并路由到 LLM 对话。
 */
public final class ChatMaidCommandHandler {

    private ChatMaidCommandHandler() {
    }

    static {
        EnhancedLittleMaidAI.LOGGER.info("EnhancedLittleMaidAI: ChatMaidCommandHandler loaded");
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("maid")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> handle(ctx.getSource(),
                                StringArgumentType.getString(ctx, "message"))));
    }

    private static int handle(CommandSourceStack src, String fullMessage) {
        if (StringUtils.isBlank(fullMessage)) return 0;

        ServerPlayer player = src.getPlayer();
        if (player == null) return 0;

        String trimmed = fullMessage.trim();

        // 解析：第一个词尝试作为名字，余下作为消息
        int firstSpace = trimmed.indexOf(' ');
        String nameHint;
        String chatMessage;

        if (firstSpace < 0) {
            // /maid 你好 → 没有名字，全文是消息
            nameHint = "";
            chatMessage = trimmed;
        } else {
            // /maid 灵梦 你好 → 第一个词是名字，后面是消息
            nameHint = trimmed.substring(0, firstSpace);
            chatMessage = trimmed.substring(firstSpace + 1).trim();
            if (chatMessage.isEmpty()) {
                chatMessage = trimmed;
                nameHint = "";
            }
        }

        EntityMaid target = findNearestMaid(player, nameHint);
        if (target == null) {
            src.sendFailure(Component.literal("附近没有找到匹配的女仆"));
            return 0;
        }

        if (!LLMUtil.isAvailable(target)) {
            src.sendFailure(Component.literal("女仆的 LLM 服务不可用，请检查配置"));
            return 0;
        }

        MaidAIChatManager chatManager = target.getAiChatManager();
        try {
            String lang = player.getLanguage();
            if (StringUtils.isBlank(lang)) lang = "zh_cn";
            ChatClientInfo clientInfo = new ChatClientInfo(
                    lang,
                    target.getDisplayName().getString(),
                    List.of()
            );
            chatManager.chat(chatMessage, clientInfo, player);

            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.debug(
                        "ChatMaid: Routed to maid {} (name={}) for player {}",
                        target.getUUID(), target.getDisplayName().getString(), player.getUUID());
            }
        } catch (Exception e) {
            EnhancedLittleMaidAI.LOGGER.warn("ChatMaid: Failed to route chat to maid", e);
            src.sendFailure(Component.literal("发送消息到女仆时出错"));
        }

        return 1;
    }

    private static EntityMaid findNearestMaid(ServerPlayer player, String nameHint) {
        double range = EnhancedConfig.CHAT_MAID_DISTANCE.get();
        List<EntityMaid> maids = player.level().getEntitiesOfClass(
                EntityMaid.class,
                player.getBoundingBox().inflate(range),
                m -> m.isAlive() && !m.isRemoved()
                        && m.isTame()
                        && m.getOwnerUUID() != null
                        && m.getOwnerUUID().equals(player.getUUID())
        );
        if (maids.isEmpty()) return null;

        maids.sort(Comparator.comparingDouble(m -> m.distanceToSqr(player)));

        if (nameHint.isEmpty()) return maids.get(0);

        String hint = nameHint.toLowerCase();
        // 精确匹配
        for (EntityMaid m : maids) {
            if (m.getDisplayName().getString().equalsIgnoreCase(nameHint)) return m;
        }
        // 前缀匹配
        for (EntityMaid m : maids) {
            if (m.getDisplayName().getString().toLowerCase().startsWith(hint)) return m;
        }
        // 回退到最近
        return maids.get(0);
    }
}
