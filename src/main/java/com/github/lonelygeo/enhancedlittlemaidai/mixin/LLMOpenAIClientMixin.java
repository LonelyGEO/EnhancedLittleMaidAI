package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.lonelygeo.enhancedlittlemaidai.util.LLMLogWriter;
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
import org.apache.logging.log4j.Logger;
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

    /**
     * 抑制父模组请求 JSON dump（TouhouLittleMaid.LOGGER.info）。
     * 由 TouhouLittleMaid.DEBUG 门控的重型日志，替换为 ELMAI 自己的截断版。
     */
    @Redirect(method = "chat",
            at = @At(value = "INVOKE",
                    target = "Lorg/apache/logging/log4j/Logger;info(Ljava/lang/String;)V"),
            remap = false, require = 0)
    private void enhanced$suppressRequestDump(Logger logger, String msg) {
        if (EnhancedConfig.SUPPRESS_PARENT_JSON_DUMP.get()) return;
        logger.info(msg);
    }

    /**
     * 抑制父模组响应 JSON dump（lambda$handle$1 中的 Logger.info）。
     * require = 0：编译器生成方法名，父模组版本升级可能失效，静默跳过。
     */
    @Redirect(method = "lambda$handle$1",
            at = @At(value = "INVOKE",
                    target = "Lorg/apache/logging/log4j/Logger;info(Ljava/lang/String;)V"),
            remap = false, require = 0)
    private void enhanced$suppressResponseDump(Logger logger, String msg) {
        if (EnhancedConfig.SUPPRESS_PARENT_JSON_DUMP.get()) return;
        logger.info(msg);
    }

    @Redirect(
            method = "chat",
            at = @At(value = "INVOKE", target = "Lcom/google/gson/Gson;toJson(Ljava/lang/Object;)Ljava/lang/String;"),
            remap = false, require = 0
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
                try {
                    String model = callback.getChatManager().getLLMModel();
                    String url = callback.getChatManager().getLLMSite().url();
                    LLMLogWriter.logRequest(callback.getClass().getSimpleName(), model, url,
                            callback.getMessages().size(), json);
                } catch (Throwable ignored) {
                }
                if (EnhancedConfig.debugLog()) {
                    EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: ReasoningContent injected into JSON, {} chars",
                            json.length());
                    EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: LLM REQUEST: {}",
                            json.length() <= 1000 ? json : json.substring(0, 997) + "...");
                }
            }
            return json;
        } finally {
            enhanced$inRedirect.remove();
        }
    }

    @Inject(method = "onTextCall", at = @At("HEAD"), remap = false)
    private void enhanced$onTextCall(ResponseCallback<ResponseChat> callback, Message firstChoice, CallbackInfo ci) {
        String role = firstChoice.getRole() != null ? firstChoice.getRole() : "unknown";
        String content = firstChoice.getContent();
        try {
            LLMLogWriter.logResponse(role, content,
                    firstChoice.getReasoningContent() != null ? firstChoice.getReasoningContent() : "");
        } catch (Throwable ignored) {
        }
        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: LLM response RECEIVED, role={}", role);
            if (content != null && !content.isEmpty()) {
                EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: LLM RESPONSE: {}",
                        content.length() <= 1000 ? content : content.substring(0, 997) + "...");
            } else {
                try { EnhancedLittleMaidAI.LOGGER.debug("EnhancedLittleMaidAI: LLM RESPONSE empty, reasoningContent={} chars",
                        firstChoice.getReasoningContent() != null ? firstChoice.getReasoningContent().length() : 0);
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * 拦截 Message.getContent()：当 content 为空但 reasoning_content 非空时，
     * 返回 reasoning_content 作为应答文本。修复 DeepSeek 推理模型偶发的空返回问题。
     */
    @Redirect(method = "onTextCall", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/ai/service/llm/openai/response/Message;"
                    + "getContent()Ljava/lang/String;"),
            remap = false, require = 0)
    private String enhanced$patchEmptyContent(Message msg) {
        String content = msg.getContent();
        if (StringUtils.isNotBlank(content)) return content;
        try {
            String rc = msg.getReasoningContent();
            if (StringUtils.isNotBlank(rc)) {
                if (EnhancedConfig.debugLog()) {
                    EnhancedLittleMaidAI.LOGGER.debug(
                            "EnhancedLittleMaidAI: Content empty, falling back to reasoning_content ({} chars)",
                            rc.length());
                }
                return rc;
            }
        } catch (Throwable ignored) {
        }
        return content;
    }

    /**
     * 拦截 onTextCall 中用于写历史的 getRawContent() 调用。
     * 父模组步骤1 用 getRawContent() 写 CappedQueue，步骤2 才用 getContent() 检查空值。
     * 此 Redirect 确保 CappedQueue 不会收到空 content（在 reasoning_content 可用时回退）。
     */
    @Redirect(method = "onTextCall", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/ai/service/llm/openai/response/Message;"
                    + "getRawContent()Ljava/lang/String;"),
            remap = false, require = 0)
    private String enhanced$patchRawContentForSave(Message msg) {
        String raw = msg.getRawContent();
        if (StringUtils.isNotBlank(raw)) return raw;
        try {
            String rc = msg.getReasoningContent();
            if (StringUtils.isNotBlank(rc)) {
                return rc;
            }
        } catch (Throwable ignored) {
        }
        return raw;
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

    /**
     * 拦截 onTextCall 中的 addAssistantHistory(String, String) 调用。
     * 当 content 和 reasoningContent 均为空白时，跳过 CappedQueue 写入，
     * 防止空消息污染对话历史导致后续 LLM 请求无法正常返回。
     * 仅影响 onTextCall 路径，不影响 onFunctionCall 中的工具调用消息。
     */
    @Redirect(method = "onTextCall", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/entity/MaidAIChatData;"
                    + "addAssistantHistory(Ljava/lang/String;Ljava/lang/String;)V"),
            remap = false, require = 0)
    private void enhanced$skipEmptyAddAssistantHistory(
            com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatData self,
            String content, String reasoningContent) {
        if (StringUtils.isNotBlank(content) || StringUtils.isNotBlank(reasoningContent)) {
            self.addAssistantHistory(content, reasoningContent);
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
                    try {
                        String rc = llmMsg.reasoningContent();
                        if (StringUtils.isNotBlank(rc)) {
                            msgObj.addProperty("reasoning_content", rc);
                        }
                    } catch (Throwable ignored) {
                        // LLMMessage.reasoningContent() 在 TLM 1.5.2 中不存在，跳过注入
                    }
                }
                msgIdx++;
            }

            return new Gson().toJson(root);
        } catch (Exception e) {
            EnhancedLittleMaidAI.LOGGER.warn(
                    "EnhancedLittleMaidAI: ReasoningContent injection failed for {} chars json: {}",
                    json.length(), e.getMessage());
            return json;
        }
    }
}
