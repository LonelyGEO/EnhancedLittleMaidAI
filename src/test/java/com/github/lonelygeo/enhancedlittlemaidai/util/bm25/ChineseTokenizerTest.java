package com.github.lonelygeo.enhancedlittlemaidai.util.bm25;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ChineseTokenizerTest {

    @Test
    public void testCjkBigramOverlap() {
        List<String> tokens = ChineseTokenizer.tokenize("铁矿石在洞穴");
        assertEquals(List.of("铁矿", "矿石", "石在", "在洞", "洞穴", "穴"), tokens);
    }

    @Test
    public void testSingleCjkChar() {
        List<String> tokens = ChineseTokenizer.tokenize("我在看");
        assertEquals(List.of("我在", "在看", "看"), tokens);
    }

    @Test
    public void testEnglishWords() {
        List<String> tokens = ChineseTokenizer.tokenize("hello world test");
        assertEquals(List.of("hello", "world", "test"), tokens);
    }

    @Test
    public void testMixedCjkAndEnglish() {
        List<String> tokens = ChineseTokenizer.tokenize("坐标 x=100 y=64");
        assertEquals(List.of("坐标", "标", "x=100", "y=64"), tokens);
    }

    @Test
    public void testEmptyAndNull() {
        assertTrue(ChineseTokenizer.tokenize("").isEmpty());
        assertTrue(ChineseTokenizer.tokenize(null).isEmpty());
    }

    @Test
    public void testPunctuationMixed() {
        List<String> tokens = ChineseTokenizer.tokenize("铁镐,钻石剑");
        assertEquals(List.of("铁镐", "镐", ",", "钻石", "石剑", "剑"), tokens);
    }

    @Test
    public void testLowerCaseEnglish() {
        List<String> tokens = ChineseTokenizer.tokenize("Hello WORLD");
        assertEquals(List.of("hello", "world"), tokens);
    }

    @Test
    public void testNumbersKeptAsIs() {
        List<String> tokens = ChineseTokenizer.tokenize("x=123 y=456");
        assertEquals(List.of("x=123", "y=456"), tokens);
    }

    @Test
    public void testWhitespaceHandling() {
        List<String> tokens = ChineseTokenizer.tokenize("  hello   world  ");
        assertEquals(List.of("hello", "world"), tokens);
    }
}
