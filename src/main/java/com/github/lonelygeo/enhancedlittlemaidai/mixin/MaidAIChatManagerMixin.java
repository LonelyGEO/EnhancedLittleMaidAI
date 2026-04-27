package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.CappedQueue;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 在 LLM 请求构建阶段，将思维宫殿记忆注入为 SYSTEM 消息。
 * 注入位置：buildMessage() 返回的消息列表 index=2（紧跟角色设定 + 压缩摘要之后）。
 */
@Mixin(value = MaidAIChatManager.class, remap = false)
public abstract class MaidAIChatManagerMixin {

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
            }
        } catch (Exception e) {
            // 记忆注入失败不应中断正常聊天流程
        }
    }
}
