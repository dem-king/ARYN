package com.aryn.cloud.promotion.api.dto;

import lombok.Data;
import java.io.Serializable;

/** 样本售价/租户/港口均由服务端重建，客户端值不成为价格或权限事实。 */
@Data
public class CampaignSimulationDTO implements Serializable {
	private String campaignDraftId;
	private Long expectedCampaignRevision;
	private String draftActivityId;
	private Long expectedRevision;
	private String replacesActivityId;
	private Long replacesRevision;
	private PromotionContextDTO sample;
	private String couponUserId;
}
