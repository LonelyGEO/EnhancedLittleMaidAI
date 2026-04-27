package com.github.tartaricacid.enhancedlittlemaidai.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ResponseCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.LLMOpenAIClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.Message;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 核心 Mixin：为 LLMOpenAIClient 注入 reasoningContent 支持。
 * <p>
 * 修改点：
 * <ul>
 *   <li>chat() — 在 ChatCompletion 序列化前，将 LLMMessage 中的 reasoningContent 注入 ChatMessage</li>
 *   <li>onTextCall() — 从 Message 提取 reasoningContent 并存入聊天历史和 LLMMessage</li>
 * </ul>
 */
@Mixin(value = LLMOpenAIClient.class, remap = false)
public abstract class LLMOpenAIClientMixin {
    @Unique
    private static final ThreadLocal<LLMCallback> enhanced$currentCallback = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<Boolean> enhanced$inRedirect = ThreadLocal.withInitial(() -> false);

    /**
     * chat() 方法入口：捕获 callback 引用，供后续 redirect 使用。
     */
    @Inject(method = "chat", at = @At("HEAD"), remap = false)
    private void enhanced$captureCallback(LLMCallback callback, CallbackInfo ci) {
        enhanced$currentCallback.set(callback);
    }

    /**
     * 拦截 GSON.toJson(chatCompletion) 调用。
     * 在序列化前，将 LLMMessage 中的 reasoningContent 注入到对应的 ChatMessage。
     * 递归安全：debug 日志会触发第二次 toJson 调用。
     */
    @SuppressWarnings("unchecked")
    @Redirect(
            method = "chat",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/gson/Gson;toJson(Ljava/lang/Object;)Ljava/lang/String;"
            ),
            remap = false
    )
    private String enhanced$patchAndToJson(Gson gson, Object src) {
        if (enhanced$inRedirect.get()) {
            return gson.toJson(src);
        }
        enhanced$inRedirect.set(true);
        try {
            ChatCompletion cc = (ChatCompletion) src;
            LLMCallback callback = enhanced$currentCallback.get();
            if (callback != null) {
                patchReasoningContent(cc, callback);
                enhanced$currentCallback.remove();
            }
            return gson.toJson(cc);
        } finally {
            enhanced$inRedirect.remove();
        }
    }

    /**
     * 在 onTextCall 入口提取 reasoningContent 并存入聊天历史。
     * 原 onTextCall 不记录历史；这里添加带 reasoningContent 的历史记录。
     */
    @Inject(
            method = "onTextCall",
            at = @At("HEAD"),
            remap = false
    )
    private void enhanced$onTextCall(
            ResponseCallback<ResponseChat> callback,
            Message firstChoice,
            CallbackInfo ci
    ) {
        if (callback instanceof LLMCallback llmCallback && llmCallback.needAddTools) {
            String rawContent = StringUtils.defaultString(getRawContentSafe(firstChoice));
            String reasoningContent = getReasoningContentSafe(firstChoice);
            llmCallback.getChatManager().addAssistantHistory(rawContent, reasoningContent);

            // 将 reasoningContent 存到消息列表最后一个 ASSISTANT 消息中
            storeReasoningContentOnLastMessage(llmCallback, reasoningContent);
        }
    }

    @Unique
    private static void patchReasoningContent(ChatCompletion cc, LLMCallback callback) {
        List<LLMMessage> llmMessages = callback.getMessages();
        List<ChatMessage> chatMessages = ((ChatCompletionAccessor) cc).getChatMessages();

        int chatIdx = 0;
        for (LLMMessage llmMsg : llmMessages) {
            if (llmMsg.role() == Role.ASSISTANT) {
                String rc = ((LLMMessageMixin) (Object) llmMsg).reasoningContent();
                if (StringUtils.isNotBlank(rc)) {
                    for (int i = chatIdx; i < chatMessages.size(); i++) {
                        ChatMessage chatMsg = chatMessages.get(i);
                        if (Role.ASSISTANT.getId().equals(chatMsg.getRole())) {
                            ((ChatMessageMixin) (Object) chatMsg).enhancedSetReasoningContent(rc);
                            chatIdx = i + 1;
                            break;
                        }
                    }
                }
            }
        }
    }

    @Unique
    private static void storeReasoningContentOnLastMessage(LLMCallback callback, @Nullable String reasoningContent) {
        if (StringUtils.isNotBlank(reasoningContent)) {
            List<LLMMessage> messages = callback.getMessages();
            if (!messages.isEmpty()) {
                LLMMessage lastMsg = messages.get(messages.size() - 1);
                if (lastMsg.role() == Role.ASSISTANT) {
                    ((LLMMessageMixin) (Object) lastMsg).enhancedSetReasoningContent(reasoningContent);
                }
            }
        }
    }

    @Unique
    private static String getRawContentSafe(Message message) {
        try {
            String raw = ((MessageMixin) (Object) message).getRawContent();
            return raw != null ? raw : message.getContent();
        } catch (Exception ignored) {
            return message.getContent();
        }
    }

    @Unique
    @Nullable
    private static String getReasoningContentSafe(Message message) {
        try {
            return ((MessageMixin) (Object) message).getReasoningContent();
        } catch (Exception ignored) {
            return null;
        }
    }
}
