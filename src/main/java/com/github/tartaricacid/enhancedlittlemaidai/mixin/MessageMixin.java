package com.github.tartaricacid.enhancedlittlemaidai.mixin;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.response.Message;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * 为 Message (LLM 响应) 添加 reasoningContent 字段和 raw content 获取方法。
 * Gson 反序列化时自动填充 @SerializedName("reasoning_content") 字段。
 */
@Mixin(value = Message.class, remap = false)
public abstract class MessageMixin {
    @Shadow
    private String content;

    @SerializedName("reasoning_content")
    @Unique
    private String reasoningContent;

    /**
     * 获取原始 content（含 &lt;think&gt; 标签），而非经过过滤的纯文本。
     */
    @Unique
    @Nullable
    public String getRawContent() {
        return this.content;
    }

    /**
     * 获取 LLM 返回的 reasoning_content 字段（思维链内容）。
     */
    @Unique
    @Nullable
    public String getReasoningContent() {
        return this.reasoningContent;
    }
}
