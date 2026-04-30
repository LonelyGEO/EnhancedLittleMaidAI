package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.lonelygeo.enhancedlittlemaidai.util.LLMResponseCache;
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
 * LLM 响应缓存 + reasoning_content JSON 注入。
 * TLM 已内置 reasoningContent 存储（LLMMessage record 字段），此处仅负责 JSON 回传。
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

        if (EnhancedConfig.debugLog()) {
            int msgCount = callback.getMessages().size();
            EnhancedLittleMaidAI.LOGGER.debug(
                    "EnhancedLittleMaidAI: LLM request ENTER cb={} msgs={}",
                    callback.getClass().getSimpleName(), msgCount);
        }

        if (LLMResponseCache.shouldCache(callback)) {
            String key = LLMResponseCache.computeKey(callback);
            if (key != null) {
                enhanced$cacheKey.set(key);
                long ttl = LLMResponseCache.getTtlMs(callback);
                ResponseChat cached = LLMResponseCache.get(key, ttl);
                if (cached != null) {
                    ci.cancel();
                    if (EnhancedConfig.debugLog()) {
                        EnhancedLittleMaidAI.LOGGER.debug(
                                "EnhancedLittleMaidAI: LLM cache HIT, skipping HTTP for {}",
                                callback.getClass().getSimpleName());
                    }
                    callback.onSuccess(cached);
                    return;
                }
            }
        }

        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: ReasoningContent injector captured callback");
        }
    }

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
                EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: ReasoningContent injected into JSON, {} chars",
                        json.length());
            }
            }
            return json;
        } finally {
            enhanced$inRedirect.remove();
        }
    }

    @Inject(method = "onTextCall", at = @At("HEAD"), remap = false)
    private void enhanced$onTextCall(ResponseCallback<ResponseChat> callback, Message firstChoice, CallbackInfo ci) {
        if (EnhancedConfig.debugLog()) {
            String role = firstChoice.getRole() != null ? firstChoice.getRole() : "unknown";
            EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: LLM response RECEIVED, role={}", role);
        }
        if (callback instanceof LLMCallback llmCallback && llmCallback.needAddTools) {
            String rawContent = StringUtils.defaultString(firstChoice.getContent());
            String reasoningContent = StringUtils.defaultString(firstChoice.getReasoningContent());
            llmCallback.getChatManager().addAssistantHistory(rawContent, StringUtils.isNotBlank(reasoningContent) ? reasoningContent : null);
            if (EnhancedConfig.debugLog() && StringUtils.isNotBlank(reasoningContent)) {
                EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: Extracted reasoningContent from LLM response");
            }
        }
    }

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
                    String rc = llmMsg.reasoningContent();
                    if (StringUtils.isNotBlank(rc)) {
                        msgObj.addProperty("reasoning_content", rc);
                    }
                }
                msgIdx++;
            }

            return new Gson().toJson(root);
        } catch (Exception e) {
            return json;
        }
    }
}
