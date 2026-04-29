package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
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
            if (site == null) {
                if (EnhancedConfig.debugLog()) {
                    EnhancedLittleMaidAI.LOGGER.debug("LLMUtil: site is null for maid {}", maid.getUUID());
                }
                return false;
            }
            if (!site.enabled()) {
                if (EnhancedConfig.debugLog()) {
                    EnhancedLittleMaidAI.LOGGER.debug("LLMUtil: site disabled for maid {}", maid.getUUID());
                }
                return false;
            }
            if (StringUtils.isBlank(site.url())) {
                if (EnhancedConfig.debugLog()) {
                    EnhancedLittleMaidAI.LOGGER.debug("LLMUtil: url blank for maid {}", maid.getUUID());
                }
                return false;
            }
            if (site instanceof LLMOpenAISite openAiSite) {
                if (StringUtils.isBlank(openAiSite.secretKey())) {
                    if (EnhancedConfig.debugLog()) {
                        EnhancedLittleMaidAI.LOGGER.debug("LLMUtil: api key blank for maid {}", maid.getUUID());
                    }
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            EnhancedLittleMaidAI.LOGGER.debug("LLMUtil: exception checking maid {}", maid.getUUID(), e);
            return false;
        }
    }
}
