package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.UserPromptContexts;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.CappedQueue;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 在 LLM 请求构建阶段注入记忆上下文，并优化 &lt;context&gt; 标签与用户消息的排列顺序。
 */
@Mixin(value = MaidAIChatManager.class, remap = false)
public abstract class MaidAIChatManagerMixin {

    /**
     * 交换 UserPromptContexts.addContext() 的输出顺序并添加优先级标记：
     * 原格式 "&lt;context&gt;状态&lt;/context&gt;\n用户提问" → "[请优先回应此消息] 用户提问\n\n&lt;context&gt;状态&lt;/context&gt;"
     * 让 LLM 优先响应用户实际提问，而非被状态数据（如饥饿度）劫持对话。
     */
    @Redirect(method = "normalChat", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/ai/manager/entity/UserPromptContexts;"
                    + "addContext(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;"
                    + "Ljava/lang/String;)Ljava/lang/String;"),
            remap = false, require = 0)
    private static String enhanced$restructureContext(EntityMaid maid, String userMsg) {
        try {
            String original = UserPromptContexts.addContext(maid, userMsg);
            String endMarker = UserPromptContexts.CONTEXT_END;
            int end = original.indexOf(endMarker);
            if (end < 0) return original;
            String contextPart = original.substring(0, end + endMarker.length());
            String userPart = original.substring(end + endMarker.length()).trim();
            return "[请优先回应此消息] " + userPart + "\n\n" + contextPart;
        } catch (Exception e) {
            return UserPromptContexts.addContext(maid, userMsg);
        }
    }

    @Inject(
            method = "buildMessage",
            at = @At("TAIL"),
            remap = false
    )
    private void enhanced$addMemoryContext(
            String setting,
            EntityMaid maid,
            CappedQueue<LLMMessage> history,
            CallbackInfoReturnable<List<LLMMessage>> cir
    ) {
        try {
            MindPalace palace = MindPalace.get(maid.getUUID());
            if (palace == null || palace.size() == 0) return;

            String query = StringUtils.substring(setting, 0, Math.min(200, setting.length()));
            String memoryXml = palace.buildMemoryContext(query, maid.blockPosition());

            if (!memoryXml.isEmpty()) {
                LLMMessage memoryMessage = LLMMessage.systemChat(maid, memoryXml);
                List<LLMMessage> messages = cir.getReturnValue();
                int insertPos = Math.min(2, messages.size());
                messages.add(insertPos, memoryMessage);

                if (EnhancedConfig.debugLog()) {
                    EnhancedLittleMaidAI.LOGGER.debug(
                            "EnhancedLittleMaidAI: Injected memory context for maid {} ({} items)",
                            maid.getUUID(), palace.size());
                }
            }
        } catch (Exception e) {
            // 记忆注入失败不中断正常聊天
        }
    }
}
