package com.aryn.cloud.promotion.api.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 不承诺销量或ROI；goodsPayable为券后商品应付，运费/积分/储值不在样本范围。 */
@Data
public class CampaignSimulationVO implements Serializable {
	private String id;
	private String campaignDraftId;
	private Long expectedCampaignRevision;
	private String draftActivityId;
	private Long draftRevision;
	private String inputDigest;
	private String factDigest;
	private LocalDateTime asOf;
	private LocalDateTime expiresAt;
	private boolean current;
	private String staleReason;
	private PromotionCalculationVO baseline;
	private PromotionCalculationVO proposed;
	private List<Risk> risks;
	private List<SkuFact> skuFacts;
	private CampaignOrderComparisonVO orderComparison;
	private Assumptions assumptions;

	@Data
	public static class Assumptions implements Serializable {
		private String scopeType;
		private String scopeValue;
		private LocalDateTime startTime;
		private LocalDateTime endTime;
		private BigDecimal totalBudget;
		private BigDecimal consumedBudget;
		private Integer totalQuota;
		private Integer consumedQuota;
		private Integer perUserLimit;
		private Long userUsage;
		private Integer giftStock;
		private Integer giftUsage;
		private BigDecimal monthlyUsage;
		private List<String> overlappingActivities;
	}

	@Data
	public static class Risk implements Serializable {
		private String code;
		private String skuId;
		private String message;
		public Risk() { }
		public Risk(String code, String skuId, String message) {
			this.code = code;
			this.skuId = skuId;
			this.message = message;
		}
	}

	@Data
	public static class SkuFact implements Serializable {
		private String skuId;
		private String costStatus;
		private BigDecimal costLower;
		private BigDecimal costUpper;
		private Integer stock;
		private Integer sampleQuantity;
	}
}
