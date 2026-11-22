package com.aryn.cloud.common.ai.contract;
import java.util.List;
public record AiEmbeddingResult(List<List<Double>> vectors, Long totalTokens, String configVersion, long elapsedMillis) {
    public AiEmbeddingResult { vectors = vectors.stream().map(List::copyOf).toList(); }
}
