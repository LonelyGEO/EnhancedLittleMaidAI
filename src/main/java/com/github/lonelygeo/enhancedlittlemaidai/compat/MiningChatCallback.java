package com.github.lonelygeo.enhancedlittlemaidai.compat;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.apache.commons.lang3.StringUtils;

import java.net.http.HttpRequest;
import java.util.List;

/**
 * 采矿 LLM 回调。
 * 继承 LLMCallback，onSuccess 时将 LLM 回复显示为女仆聊天气泡。
 * needAddTools = false，不触发记忆提取链。
 */
public class MiningChatCallback extends LLMCallback {

    private final long waitingBubbleId;

    public MiningChatCallback(
            MaidAIChatManager chatManager,
            List<LLMMessage> messages,
            long waitingBubbleId
    ) {
        super(chatManager, messages, true);
        this.needAddTools = false;
        this.waitingBubbleId = waitingBubbleId;
    }

    @Override
    public void onSuccess(ResponseChat responseChat) {
        String chatText = responseChat.getChatText();
        EntityMaid maid = getMaid();

        if (maid != null && StringUtils.isNotBlank(chatText)) {
            try {
                maid.getChatBubbleManager().addLLMChatText(chatText, waitingBubbleId);
            } catch (Exception e) {
                // 静默失败
            }
        }
    }

    @Override
    public void onFailure(HttpRequest request, Throwable throwable, int errorCode) {
        EntityMaid maid = getMaid();
        if (maid != null) {
            try {
                maid.getChatBubbleManager().removeChatBubble(waitingBubbleId);
            } catch (Exception ignored) {
            }
        }
    }
}
