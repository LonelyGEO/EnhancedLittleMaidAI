package com.github.lonelygeo.enhancedlittlemaidai.util;

/**
 * LLM prompt 共享常量，避免重复指令漂移。
 */
public final class PromptConstants {

    public static final String NO_ACTION_DESCRIPTION =
            "只输出一句纯对话，严格禁止：（...）或*...*等任何动作描写、旁白、心理活动。";

    public static final String TRANSLATE_ENGLISH_NAMES =
            "注意：英文地名物品名请转换为中文Minecraft玩家熟知的名词。";

    private PromptConstants() {
    }
}
