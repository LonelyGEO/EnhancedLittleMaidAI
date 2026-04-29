package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.lonelygeo.enhancedlittlemaidai.util.LLMResponseCache;
import com.github.lonelygeo.enhancedlittlemaidai.util.ReasoningContentStore;
import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ResponseCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.LLMOpenAIClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.Message;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.List;

/**
 * 为 LLMOpenAIClient 注入 reasoningContent 支持。
 * 不依赖 Mixin 字段 — 直接在 JSON 层面注入 reasoning_content。
 */
@Mixin(value = LLMOpenAIClient.class, remap = false)
public abstract class LLMOpenAIClientMixin {
    @Unique
    private static final ThreadLocal<LLMCallback> enhanced$currentCallback = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Boolean> enhanced$inRedirect = ThreadLocal.withInitial(() -> false);
    @Unique
    private static final ThreadLocal<String> enhanced$cacheKey = new ThreadLocal<>();

    @Inject(method = "chat", at = @At("HEAD"), cancellable = true, remap = false)
    private void enhanced$captureCallback(LLMCallback callback, CallbackInfo ci) {
        enhanced$currentCallback.set(callback);

        if (LLMResponseCache.shouldCache(callback)) {
            String key = LLMResponseCache.computeKey(callback);
            if (key != null) {
                enhanced$cacheKey.set(key);
                long ttl = LLMResponseCache.getTtlMs(callback);
                ResponseChat cached = LLMResponseCache.get(key, ttl);
                if (cached != null) {
                    ci.cancel();
                    callback.onSuccess(cached);
                    if (EnhancedConfig.debugLog()) {
                        EnhancedLittleMaidAI.LOGGER.debug(
                                "EnhancedLittleMaidAI: Cache hit for callback {}", callback.getClass().getSimpleName());
                    }
                    return;
                }
            }
        }

        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: ReasoningContent injector captured callback");
        }
    }

    /**
     * 拦截 Gson.toJson，JSON 序列化后注入 reasoning_content 字段。
     */
    @Redirect(
            method = "chat",
            at = @At(value = "INVOKE", target = "Lcom/google/gson/Gson;toJson(Ljava/lang/Object;)Ljava/lang/String;"),
            remap = false
    )
    private String enhanced$patchAndToJson(Gson gson, Object src) {
        if (enhanced$inRedirect.get()) {
            return gson.toJson(src);
        }
        enhanced$inRedirect.set(true);
        try {
            String json = gson.toJson(src);
            LLMCallback callback = enhanced$currentCallback.get();
            if (callback != null) {
                json = injectReasoningContent(json, callback.getMessages());
                enhanced$currentCallback.remove();
                if (EnhancedConfig.debugLog()) {
                    EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: ReasoningContent injected into JSON");
                }
            }
            return json;
        } finally {
            enhanced$inRedirect.remove();
        }
    }

    /**
     * 在 onTextCall 中记录 reasoningContent 到历史。
     */
    @Inject(method = "onTextCall", at = @At("HEAD"), remap = false)
    private void enhanced$onTextCall(ResponseCallback<ResponseChat> callback, Message firstChoice, CallbackInfo ci) {
        if (callback instanceof LLMCallback llmCallback && llmCallback.needAddTools) {
            String rawContent = StringUtils.defaultString(firstChoice.getContent());
            String reasoningContent = ReasoningContentStore.getFromMessage(firstChoice);
            llmCallback.getChatManager().addAssistantHistory(rawContent, reasoningContent);
            storeReasoningContent(llmCallback, reasoningContent);
            if (EnhancedConfig.debugLog() && reasoningContent != null) {
                EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: Extracted reasoningContent from LLM response");
            }
        }
    }

    /**
     * 在 onTextCall 尾部，将 LLM 回复存入缓存供后续重用。
     */
    @Inject(method = "onTextCall", at = @At("TAIL"), remap = false)
    private void enhanced$cacheOnTextCall(ResponseCallback<ResponseChat> callback, Message firstChoice,
                                           CallbackInfo ci) {
        try {
            String key = enhanced$cacheKey.get();
            if (key != null && callback instanceof LLMCallback llmCallback
                    && LLMResponseCache.shouldCache(llmCallback)) {
                String content = StringUtils.defaultString(firstChoice.getContent());
                if (StringUtils.isNotBlank(content)) {
                    LLMResponseCache.put(key, content);
                }
            }
        } finally {
            enhanced$cacheKey.remove();
        }
    }

    /**
     * 在已有 JSON 的基础上，为 ASSISTANT 消息注入 reasoning_content 字段。
     */
    @Unique
    private static String injectReasoningContent(String json, List<LLMMessage> llmMessages) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray messages = root.getAsJsonArray("messages");
            if (messages == null) {
                return json;
            }

            Iterator<LLMMessage> llmIter = llmMessages.iterator();
            int msgIdx = 0;
            while (llmIter.hasNext() && msgIdx < messages.size()) {
                LLMMessage llmMsg = llmIter.next();
                JsonElement el = messages.get(msgIdx);
                if (!el.isJsonObject()) {
                    msgIdx++;
                    continue;
                }
                JsonObject msgObj = el.getAsJsonObject();
                String role = msgObj.has("role") ? msgObj.get("role").getAsString() : "";

                if (llmMsg.role() == Role.ASSISTANT && "assistant".equals(role)) {
                    String rc = ReasoningContentStore.get(llmMsg);
                    if (rc == null) rc = llmMsg.reasoningContent();
                    if (StringUtils.isNotBlank(rc)) {
                        msgObj.addProperty("reasoning_content", rc);
                    }
                }
                msgIdx++;
            }

            return new Gson().toJson(root);
        } catch (Exception e) {
            // Silently fall back to unmodified JSON
            return json;
        }
    }

    @Unique
    private static void storeReasoningContent(LLMCallback callback, String reasoningContent) {
        if (StringUtils.isNotBlank(reasoningContent)) {
            List<LLMMessage> messages = callback.getMessages();
            if (!messages.isEmpty()) {
                LLMMessage lastMsg = messages.get(messages.size() - 1);
                if (lastMsg.role() == Role.ASSISTANT) {
                    ReasoningContentStore.put(lastMsg, reasoningContent);
                }
            }
        }
    }
}
