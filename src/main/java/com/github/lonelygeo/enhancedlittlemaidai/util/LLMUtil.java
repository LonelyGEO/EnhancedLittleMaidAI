package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.LLMOpenAISite;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.apache.commons.lang3.StringUtils;

/**
 * LLM 可用性统一校验。
 * 检查 site 启用 + URL 已填 + API Key 已填。
 */
public final class LLMUtil {

    private LLMUtil() {
    }

    public static boolean isAvailable(EntityMaid maid) {
        try {
            var site = maid.getAiChatManager().getLLMSite();
            if (site == null) return false;
            if (!site.enabled()) return false;
            if (StringUtils.isBlank(site.url())) return false;
            if (site instanceof LLMOpenAISite openAiSite) {
                return StringUtils.isNotBlank(openAiSite.secretKey());
            }
            // 非 OpenAI 类站点（基本不存在）→ url 不为空视为可用
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
