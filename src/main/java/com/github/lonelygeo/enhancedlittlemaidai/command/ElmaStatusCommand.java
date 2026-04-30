package com.github.lonelygeo.enhancedlittlemaidai.command;

import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.lonelygeo.enhancedlittlemaidai.util.EnvironmentEventDetector;
import com.github.lonelygeo.enhancedlittlemaidai.util.InterMaidChatManager;
import com.github.lonelygeo.enhancedlittlemaidai.util.LLMUtil;
import com.github.lonelygeo.enhancedlittlemaidai.util.ProactiveChatManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class ElmaStatusCommand {
    private ElmaStatusCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("status")
                .then(Commands.argument("maid", EntityArgument.entities())
                        .executes(ctx -> showStatus(ctx.getSource(),
                                EntityArgument.getEntities(ctx, "maid"))));
    }

    private static int showStatus(CommandSourceStack src,
                                   java.util.Collection<? extends net.minecraft.world.entity.Entity> entities) {
        EntityMaid maid = null;
        for (var e : entities) {
            if (e instanceof EntityMaid m) { maid = m; break; }
        }
        if (maid == null) {
            src.sendFailure(Component.literal("目标不是女仆"));
            return 0;
        }

        String name = maid.getDisplayName().getString();
        UUID uuid = maid.getUUID();
        long gameTime = maid.level().getGameTime();

        MindPalace palace = MindPalace.get(uuid);
        int mainCount = palace != null ? palace.size() : 0;
        int socialCount = palace != null ? palace.getSocialStore().size() : 0;

        // 主动聊天
        Long lastChat = ProactiveChatManager.getLastTime(uuid);
        int chatToday = ProactiveChatManager.getCount(uuid);
        long chatCd = EnhancedConfig.COOLDOWN_TICKS.get();
        long chatRemain = lastChat != null ? Math.max(0, chatCd - (gameTime - lastChat)) / 20 : 0;
        boolean chatEnabled = EnhancedConfig.PROACTIVE_CHAT_ENABLED.get();

        // 环境事件
        int eventToday = EnvironmentEventDetector.getEventCount(uuid);
        long eventCd = EnhancedConfig.EVENT_COOLDOWN_TICKS.get();
        Long lastEvent = EnvironmentEventDetector.getLastEventTime(uuid);
        long eventRemain = lastEvent != null ? Math.max(0, eventCd - (gameTime - lastEvent)) / 20 : 0;

        // 女仆社交
        int socialToday = InterMaidChatManager.getDayCount(uuid);
        int socialGlobal = InterMaidChatManager.getGlobalDayCount();
        boolean socialDeciding = InterMaidChatManager.isDeciding(uuid);
        boolean socialBusy = InterMaidChatManager.isBusy(uuid);

        // LLM
        boolean llmOk = LLMUtil.isAvailable(maid);
        String llmLabel = llmOk ? "deepseek \u2713" : "\u2717 不可用";

        // TTS
        boolean ttsDisabled = EnhancedConfig.OVERRIDE_TTS_DISABLED.get();

        String status = name + " ELMAI 状态:\n" +
                "  记忆: " + mainCount + " 条 | 社交记忆: " + socialCount + " 条\n" +
                "  主动聊天: " + (chatEnabled ? "开启" : "关闭") +
                " | 冷却剩余 " + chatRemain + "s" +
                " | 今日 " + chatToday + "/" + EnhancedConfig.MAX_CHATS_PER_DAY.get() + " 次\n" +
                "  环境事件: 今日 " + eventToday + "/" + EnhancedConfig.EVENT_MAX_PER_DAY.get() +
                " 次 | 冷却剩余 " + eventRemain + "s\n" +
                "  女仆社交: " + (socialBusy ? "忙碌" : socialDeciding ? "决策中" : "空闲") +
                " | 今日 " + socialToday + "/" + EnhancedConfig.INTER_MAID_MAX_PER_DAY.get() +
                " 次 | 全局今日 " + socialGlobal + "/" + EnhancedConfig.INTER_MAID_MAX_GLOBAL_PER_DAY.get() + " 次\n" +
                "  LLM: " + llmLabel + "\n" +
                "  语音: " + (ttsDisabled ? "已禁用 (ELMAI全局)" : "正常");

        src.sendSuccess(() -> Component.literal(status), false);
        return 1;
    }
}
