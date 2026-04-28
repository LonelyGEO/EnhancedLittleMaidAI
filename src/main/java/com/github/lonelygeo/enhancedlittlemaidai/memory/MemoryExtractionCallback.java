package com.github.lonelygeo.enhancedlittlemaidai.memory;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 子代理 Callback：专门用于向 LLM 请求记忆提取。
 * 继承 LLMCallback，onSuccess 时解析 LLM 返回结果为 MemoryItem 列表。
 */
public class MemoryExtractionCallback extends LLMCallback {

    private final CompletableFuture<List<MemoryItem>> future;

    public MemoryExtractionCallback(
            MaidAIChatManager chatManager,
            List<LLMMessage> messages,
            CompletableFuture<List<MemoryItem>> future
    ) {
        super(chatManager, messages, true);
        this.needAddTools = false;
        this.future = future;
    }

    @Override
    public void onSuccess(ResponseChat responseChat) {
        String text = responseChat.getChatText();
        List<MemoryItem> items = MemoryResponseParser.parse(text);
        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.debug(
                    "EnhancedLittleMaidAI: Memory extraction LLM returned {} items", items.size());
        }
        future.complete(items);
    }

    @Override
    public void onFailure(HttpRequest request, Throwable throwable, int errorCode) {
        EnhancedLittleMaidAI.LOGGER.warn(
                "EnhancedLittleMaidAI: Memory extraction LLM call failed with code {}", errorCode);
        future.completeExceptionally(
                throwable != null ? throwable : new RuntimeException("Memory extraction failed: " + errorCode));
    }

    public static String buildPrompt(List<String> recentMessages) {
        return """
                You are a memory extraction system. From the conversation below, extract information
                worth permanently remembering. Output one memory per line in this format:
                [CATEGORY] content | location:x,y,z,dimension (optional) | importance:N
                
                Rules:
                - CATEGORY must be one of: PLACE, PERSON, EVENT, PREFERENCE, KNOWLEDGE
                - content must be under 80 characters
                - Only extract genuinely important long-term information
                - Skip casual greetings and trivial chitchat
                - Rate each memory importance from 1 (low) to 5 (high)
                - Output ONLY the memory lines, nothing else
                
                Example output:
                [PLACE] 钻石在地下室第二个箱子里 | location:200,64,-100,minecraft:overworld | importance:4
                [PREFERENCE] 主人喜欢用铁镐挖矿 | importance:3
                [EVENT] 矿洞里遭遇了爬行者破坏通道 | location:150,12,50,minecraft:overworld | importance:5
                
                Conversation:
                %s
                """.formatted(String.join("\n---\n", recentMessages));
    }
}
