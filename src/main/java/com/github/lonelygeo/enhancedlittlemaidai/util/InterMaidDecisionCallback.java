package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.apache.commons.lang3.StringUtils;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.regex.Pattern;

/**
 * B 接受提案的 LLM 决策回调。
 * onSuccess 在完整文本中搜索 ACCEPT/REJECT，兼容推理模型的角色扮演输出。
 */
public class InterMaidDecisionCallback extends LLMCallback {

    private static final Pattern PAREN_CONTENT = Pattern.compile("[（(][^）)]*[）)]");
    private static final Pattern STAR_CONTENT = Pattern.compile("\\*[^*]*\\*");

    private final EntityMaid maidA;
    private final EntityMaid maidB;

    public InterMaidDecisionCallback(
            MaidAIChatManager chatManager,
            List<LLMMessage> messages,
            EntityMaid maidA,
            EntityMaid maidB
    ) {
        super(chatManager, messages, true);
        this.needAddTools = false;
        this.maidA = maidA;
        this.maidB = maidB;
    }

    @Override
    public void onSuccess(ResponseChat responseChat) {
        String text = responseChat.getChatText();
        if (StringUtils.isBlank(text)) {
            reject();
            return;
        }

        String upper = text.trim().toUpperCase();
        if ("ACCEPT".equals(upper) || "REJECT".equals(upper)) {
            if ("ACCEPT".equals(upper)) { accept(); } else { reject(); }
            return;
        }

        String cleaned = PAREN_CONTENT.matcher(upper).replaceAll("");
        cleaned = STAR_CONTENT.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("[\\s,.!?;:\"]+", " ").trim();

        if (cleaned.contains("ACCEPT")) {
            accept();
        } else {
            reject();
        }
    }

    @Override
    public void onFailure(HttpRequest request, Throwable throwable, int errorCode) {
        reject();
    }

    private void accept() {
        InterMaidChatManager.releaseDecisionSlot();
        // handleAcceptance → tryStartConversation 需要访问 Level.getEntitiesOfClass()
        // 必须运行在 server thread 上
        net.minecraft.server.MinecraftServer server = maidB.getServer();
        if (server != null) {
            server.submit(() -> InterMaidChatManager.handleAcceptance(maidB));
        } else {
            InterMaidChatManager.handleAcceptance(maidB);
        }
        InterMaidChatManager.finishDeciding(maidB.getUUID());
        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.info(
                    "InterMaidChat: LLM ACCEPT {}", maidB.getUUID());
        }
    }

    private void reject() {
        InterMaidChatManager.releaseDecisionSlot();
        long gameTime = maidB.level().getGameTime();
        InterMaidChatManager.markRejected(maidA.getUUID(), maidB.getUUID(), gameTime);
        InterMaidChatManager.finishDeciding(maidB.getUUID());
        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.info(
                    "InterMaidChat: LLM decision REJECT: {} ← {}",
                    maidB.getUUID(), maidA.getUUID());
        }
    }

    /** 构建决策 prompt。指令前置，角色设定后置，防止推理模型角色淹没指令。 */
    public static String buildDecisionPrompt(EntityMaid b, EntityMaid a) {
        String aName = a.getDisplayName().getString();
        String setting = b.getAiChatManager().customSetting;
        if (StringUtils.isBlank(setting)) {
            setting = b.getDisplayName().getString() + "，一位女仆。";
        }

        return "[系统指令] 做出简单决定。只输出一个单词：ACCEPT 或 REJECT。"
                + "\n严格禁止：输出任何中文、日文、英文句子、动作描写、想法、表情、括号、（）、*...*、标点。"
                + "\n违反会导致系统错误。"
                + "\n\n" + setting
                + "\n\n刚才" + aName + "邀请你聊天。只输出 ACCEPT 或 REJECT。";
    }
}
