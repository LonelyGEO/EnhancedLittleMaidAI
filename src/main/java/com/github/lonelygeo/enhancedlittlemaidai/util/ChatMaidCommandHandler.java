package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;

/**
 * 聊天栏 /maid 命令处理器。
 * 玩家在聊天栏输入 "/maid [名字] 消息" → 匹配最近女仆 → 发起 LLM 对话。
 * 消息不显示在公共聊天栏。
 */
public final class ChatMaidCommandHandler {

    private static final double MAID_CHAT_RANGE = 16.0;
    private static final double MAID_CHAT_RANGE_SQ = MAID_CHAT_RANGE * MAID_CHAT_RANGE;

    private ChatMaidCommandHandler() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.register(ChatMaidCommandHandler.class);
        EnhancedLittleMaidAI.LOGGER.info("EnhancedLittleMaidAI: ChatMaidCommandHandler registered");
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        String message = event.getMessage().getString();
        if (StringUtils.isBlank(message)) return;

        String trimmed = message.trim();
        if (!trimmed.startsWith("/maid") || trimmed.length() <= "/maid".length()) return;

        String afterPrefix = trimmed.substring("/maid".length()).trim();
        if (afterPrefix.isEmpty()) return;

        ServerPlayer player = event.getPlayer();
        if (player == null) return;

        // 解析可选名字和消息
        String maidName;
        String chatMessage;
        int firstSpace = afterPrefix.indexOf(' ');
        if (firstSpace < 0) {
            // 只有名字，没有消息 → 忽略
            return;
        }
        // "/maid 灵梦 你好" → maidName="灵梦", chatMessage="你好"
        // "/maid 你好" → maidName="你好", chatMessage="" (first word is the message, ignore)
        // 简单策略：取第一个词作为候选名字，后面作为消息
        maidName = afterPrefix.substring(0, firstSpace);
        chatMessage = afterPrefix.substring(firstSpace + 1).trim();
        if (chatMessage.isEmpty()) {
            // "/maid 灵梦" — 可能是名字也可能是短语，忽略没有消息的情况
            return;
        }

        // 找到最近的女仆
        EntityMaid target = findNearestMaid(player, maidName);
        if (target == null) {
            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.debug(
                        "ChatMaid: No matching maid for player {}, name={}",
                        player.getUUID(), maidName);
            }
            return;
        }

        // 取消公共聊天栏显示
        event.setCanceled(true);

        if (!LLMUtil.isAvailable(target)) {
            player.sendSystemMessage(Component.literal("女仆的 LLM 服务不可用，请检查配置"));
            return;
        }

        MaidAIChatManager chatManager = target.getAiChatManager();
        try {
            // 构建 server-side ChatClientInfo
            String lang = player.getLanguage();
            if (StringUtils.isBlank(lang)) lang = "zh_cn";
            var clientInfo = new com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo(
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
        }
    }

    /**
     * 找到玩家附近最近的一只女仆。
     * 如果提供了名字，优先精确匹配，其次前缀匹配，最后模糊包含。
     */
    private static EntityMaid findNearestMaid(ServerPlayer player, String nameHint) {
        List<EntityMaid> maids = player.level().getEntitiesOfClass(
                EntityMaid.class,
                player.getBoundingBox().inflate(MAID_CHAT_RANGE),
                m -> m.isAlive() && !m.isRemoved()
                        && m.isTame()
                        && m.getOwnerUUID() != null
                        && m.getOwnerUUID().equals(player.getUUID())
        );
        if (maids.isEmpty()) return null;

        // 如果只有一个女仆，不管名字直接返回
        if (maids.size() == 1) return maids.get(0);

        // 按距离排序
        maids.sort(Comparator.comparingDouble(m -> m.distanceToSqr(player)));

        String hint = nameHint.toLowerCase();
        // 尝试精确匹配
        for (EntityMaid m : maids) {
            String displayName = m.getDisplayName().getString();
            if (displayName.equalsIgnoreCase(nameHint)) return m;
        }
        // 尝试前缀匹配
        for (EntityMaid m : maids) {
            String displayName = m.getDisplayName().getString();
            if (displayName.toLowerCase().startsWith(hint)) return m;
        }
        // 回退到最近的女仆
        return maids.get(0);
    }
}
