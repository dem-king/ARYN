package com.aryn.cloud.common.ai.contract;
public enum AiUsage {
    PROCUREMENT_PARSE(AiCapability.STRUCTURED_TEXT), SKU_RERANK(AiCapability.STRUCTURED_TEXT),
    MARKETING_DRAFT(AiCapability.STRUCTURED_TEXT), PRODUCT_COPY(AiCapability.STRUCTURED_TEXT),
    CATALOG_EMBEDDING(AiCapability.EMBEDDING);
    private final AiCapability capability;
    AiUsage(AiCapability capability) { this.capability = capability; }
    public AiCapability capability() { return capability; }
}
