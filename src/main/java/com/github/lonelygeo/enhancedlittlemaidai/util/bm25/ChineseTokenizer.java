package com.github.lonelygeo.enhancedlittlemaidai.util.bm25;

import java.util.ArrayList;
import java.util.List;

/**
 * 字符级 bigram 分词器：
 * - CJK 字符（U+4E00–U+9FFF, U+3400–U+4DBF, U+F900–U+FAFF）用 bigram
 * - 英文/数字按空白分割
 * - 示例："铁矿石在洞穴" → ["铁矿", "矿石", "在洞", "洞穴"]
 * - 示例："x=100 y=64" → ["x=100", "y=64"]
 */
public final class ChineseTokenizer {
    private ChineseTokenizer() {}

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) return tokens;

        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCJK(c)) {
                if (!buffer.isEmpty()) {
                    tokens.add(buffer.toString().toLowerCase());
                    buffer.setLength(0);
                }
                if (i + 1 < text.length() && isCJK(text.charAt(i + 1))) {
                    tokens.add(String.valueOf(c) + text.charAt(i + 1));
                } else {
                    tokens.add(String.valueOf(c));
                }
            } else if (Character.isWhitespace(c)) {
                if (!buffer.isEmpty()) {
                    tokens.add(buffer.toString().toLowerCase());
                    buffer.setLength(0);
                }
            } else {
                buffer.append(c);
            }
        }
        if (!buffer.isEmpty()) {
            tokens.add(buffer.toString().toLowerCase());
        }
        return tokens;
    }

    private static boolean isCJK(char c) {
        return Character.isIdeographic(c);
    }
}
