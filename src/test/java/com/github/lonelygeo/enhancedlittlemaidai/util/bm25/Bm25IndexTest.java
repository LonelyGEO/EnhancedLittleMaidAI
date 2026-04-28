package com.github.lonelygeo.enhancedlittlemaidai.util.bm25;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class Bm25IndexTest {
    private Bm25Index index;

    @Before
    public void setUp() {
        index = new Bm25Index();
    }

    @Test
    public void testAddAndSize() {
        assertEquals(0, index.size());
        index.add("doc1", "铁矿在洞穴");
        assertEquals(1, index.size());
        index.add("doc2", "钻石在地下室");
        assertEquals(2, index.size());
    }

    @Test
    public void testAddDuplicateReplaces() {
        index.add("doc1", "铁矿石在洞穴");
        index.add("doc1", "铁矿在洞穴");
        assertEquals(1, index.size());
        List<Bm25Index.ScoredDoc> results = index.search("铁矿", 5);
        assertEquals(1, results.size());
    }

    @Test
    public void testSearchReturnsResults() {
        index.add("doc1", "铁矿石在洞穴深处");
        index.add("doc2", "钻石在地下室箱子里");
        index.add("doc3", "铁镐可以用来挖矿");

        List<Bm25Index.ScoredDoc> results = index.search("铁矿", 3);
        assertFalse(results.isEmpty());
        for (Bm25Index.ScoredDoc doc : results) {
            assertTrue("Score must be positive", doc.score() > 0);
        }
    }

    @Test
    public void testTopKLimit() {
        for (int i = 1; i <= 10; i++) {
            index.add("doc" + i, "铁");
        }
        List<Bm25Index.ScoredDoc> results = index.search("铁", 3);
        assertEquals(3, results.size());
    }

    @Test
    public void testTopKGreaterThanTotal() {
        index.add("doc1", "铁");
        index.add("doc2", "钻石");
        List<Bm25Index.ScoredDoc> results = index.search("铁", 10);
        assertEquals(1, results.size());
    }

    @Test
    public void testEmptySearchReturnsEmpty() {
        index.add("doc1", "内容");
        assertTrue(index.search("", 5).isEmpty());
    }

    @Test
    public void testRemoveDocument() {
        index.add("doc1", "铁矿");
        index.add("doc2", "钻石");
        assertEquals(2, index.size());

        index.remove("doc1");
        assertEquals(1, index.size());

        List<Bm25Index.ScoredDoc> results = index.search("铁矿", 5);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testRemoveNonexistentNoError() {
        index.add("doc1", "铁矿");
        index.remove("nonexistent");
        assertEquals(1, index.size());
    }

    @Test
    public void testScoreDescending() {
        index.add("doc1", "钻石钻石钻石");
        index.add("doc2", "铁矿钻石");
        index.add("doc3", "钻石");

        List<Bm25Index.ScoredDoc> results = index.search("钻石", 3);
        assertEquals(3, results.size());
        double prev = Double.MAX_VALUE;
        for (Bm25Index.ScoredDoc doc : results) {
            assertTrue(doc.score() <= prev);
            prev = doc.score();
        }
    }

    @Test
    public void testAddAfterRemove() {
        index.add("doc1", "铁矿");
        index.remove("doc1");
        index.add("doc1", "钻石矿");
        assertEquals(1, index.size());
        List<Bm25Index.ScoredDoc> results = index.search("钻石", 5);
        assertEquals(1, results.size());
        assertEquals("doc1", results.get(0).docId());
    }

    @Test
    public void testAddRemoveRebuild() {
        index.add("d1", "钻石在地下室");
        index.add("d2", "铁矿在洞穴");
        index.add("d3", "红石在山上");

        index.remove("d2");
        index.add("d4", "金矿石在河边");

        // 验证旧文档 d2 不在结果中
        List<Bm25Index.ScoredDoc> results = index.search("铁矿", 5);
        assertTrue(results.isEmpty());

        // 新文档可检索
        results = index.search("金矿", 5);
        assertEquals(1, results.size());
        assertEquals("d4", results.get(0).docId());
    }
}
