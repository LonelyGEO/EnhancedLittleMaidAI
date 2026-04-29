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
import java.util.UUID;

/**
 * B 接受提案的 LLM 决策回调。
 * LLM 基于社交记忆判断是否接受对话。
 */
public class InterMaidDecisionCallback extends LLMCallback {

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
        String firstWord = upper.split("[\\s,.!?;:]+")[0];
        if ("ACCEPT".equals(firstWord)) {
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
        InterMaidChatManager.clearProposal(maidB.getUUID());
        InterMaidChatManager.handleAcceptance(maidB);
        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.info(
                    "InterMaidChat: LLM ACCEPT {}", maidB.getUUID());
        }
    }

    private void reject() {
        long gameTime = maidB.level().getGameTime();
        InterMaidChatManager.markRejected(maidA.getUUID(), maidB.getUUID(), gameTime);
        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.info(
                    "InterMaidChat: LLM decision REJECT: {} ← {}", 
                    maidB.getUUID(), maidA.getUUID());
        }
    }

    /** 构建决策 prompt */
    public static String buildDecisionPrompt(EntityMaid b, EntityMaid a) {
        StringBuilder sb = new StringBuilder();
        String setting = b.getAiChatManager().customSetting;
        if (StringUtils.isBlank(setting)) {
            setting = b.getDisplayName().getString() + "，一位女仆。";
        }
        sb.append(setting);
        sb.append("\n\n你看到了").append(a.getDisplayName().getString())
                .append("，她想和你聊几句。");
        sb.append("\n请根据你的性格判断是否愿意和她聊天。");
        sb.append("\n只回答 ACCEPT 或 REJECT，不要多说任何话。");
        return sb.toString();
    }
}
