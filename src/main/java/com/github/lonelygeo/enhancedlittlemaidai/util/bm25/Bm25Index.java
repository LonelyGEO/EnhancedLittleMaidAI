package com.github.lonelygeo.enhancedlittlemaidai.util.bm25;

import java.util.*;

/**
 * BM25 倒排索引。
 *
 * BM25 参数：
 *   k1 = 1.2  （词频饱和度系数）
 *   b  = 0.75 （文档长度归一化系数）
 *
 * 公式：score(q, d) = Σ IDF(t) × (tf(t,d) × (k1+1)) / (tf(t,d) + k1 × (1-b + b × |d|/avgdl))
 * 其中 IDF(t) = ln((N - n(t) + 0.5) / (n(t) + 0.5) + 1)
 */
public class Bm25Index {
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final Map<String, Document> documents = new HashMap<>();
    private final Map<String, Map<String, Integer>> invertedIndex = new HashMap<>();
    private long totalLength = 0;

    public void add(String docId, String content) {
        remove(docId);
        List<String> tokens = ChineseTokenizer.tokenize(content);
        if (tokens.isEmpty()) return;

        documents.put(docId, new Document(docId, tokens));
        totalLength += tokens.size();

        for (String token : tokens) {
            invertedIndex.computeIfAbsent(token, k -> new HashMap<>())
                    .merge(docId, 1, Integer::sum);
        }
    }

    public void remove(String docId) {
        Document old = documents.remove(docId);
        if (old == null) return;
        totalLength -= old.tokens.size();

        for (String token : old.tokens) {
            Map<String, Integer> postings = invertedIndex.get(token);
            if (postings != null) {
                Integer oldCount = postings.get(docId);
                if (oldCount != null) {
                    int newCount = oldCount - 1;
                    if (newCount <= 0) {
                        postings.remove(docId);
                    } else {
                        postings.put(docId, newCount);
                    }
                }
                if (postings.isEmpty()) {
                    invertedIndex.remove(token);
                }
            }
        }
    }

    public List<ScoredDoc> search(String query, int topK) {
        List<String> queryTokens = ChineseTokenizer.tokenize(query);
        if (queryTokens.isEmpty()) return List.of();

        int N = documents.size();
        double avgdl = N > 0 ? (double) totalLength / N : 1.0;

        Map<String, Double> scores = new HashMap<>();
        for (String term : queryTokens) {
            Map<String, Integer> postings = invertedIndex.get(term);
            if (postings == null) continue;
            int df = postings.size();

            double idf = Math.log((N - df + 0.5) / (df + 0.5) + 1.0);

            for (Map.Entry<String, Integer> entry : postings.entrySet()) {
                String docId = entry.getKey();
                int tf = entry.getValue();
                Document doc = documents.get(docId);
                if (doc == null) continue;

                double tfNorm = (tf * (K1 + 1.0))
                        / (tf + K1 * (1.0 - B + B * doc.tokens.size() / avgdl));
                scores.merge(docId, idf * tfNorm, Double::sum);
            }
        }

        double normFactor = Math.max(1, queryTokens.size());
        return scores.entrySet().stream()
                .map(e -> new ScoredDoc(e.getKey(), e.getValue() / normFactor))
                .sorted(Comparator.comparingDouble(ScoredDoc::score).reversed())
                .limit(topK)
                .toList();
    }

    public int size() {
        return documents.size();
    }

    public record ScoredDoc(String docId, double score) {}

    private record Document(String id, List<String> tokens) {}
}
