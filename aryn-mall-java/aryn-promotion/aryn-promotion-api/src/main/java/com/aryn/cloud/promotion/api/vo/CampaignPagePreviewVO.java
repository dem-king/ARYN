package com.aryn.cloud.promotion.api.vo;
import lombok.Data;
import java.io.Serializable;
/** 仅受权管理员获取情景事实；普通分享预览令牌不携带样本合同价。 */
@Data
public class CampaignPagePreviewVO implements Serializable {
	private String campaignDraftId;
	private Long campaignRevision;
	private PageDesignEditorVO page;
	private CampaignSimulationVO simulation;
	private boolean current;
	private String pageDigest;
	private java.util.List<Goods> goods;
	@Data public static class Goods implements Serializable {
		private String id;
		private String skuId;
		private String name;
		private java.math.BigDecimal price;
		private java.math.BigDecimal originalPrice;
		private Integer stock;
	}

}
