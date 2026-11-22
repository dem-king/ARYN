package com.aryn.cloud.promotion.api.dto;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
/** 可保存不完整意图；生成前必须补齐业务约束，模型不得推定采购主体或让利上限。 */
@Data
public class CampaignDraftSaveDTO implements Serializable {
	private Long expectedRevision;
	private String title;
	private String intent;
	private String purchaseScene;
	private String sampleUserId;
	private String vesselId;
	private String buyerOrgId;
	private String vesselCallId;
	private List<String> skuIds;
	private LocalDateTime startTime;
	private LocalDateTime endTime;
	private BigDecimal budget;
	private BigDecimal maxDiscountPercent;
	private B2bActivitySaveDTO activity;
	private CampaignCouponDraftDTO coupon;
	private String referencedPolicyId;
	private CampaignPolicyCommandDTO policy;
	private String calendarId;
	private String primaryType;
}
