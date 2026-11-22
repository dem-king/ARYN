package com.aryn.cloud.promotion.api.dto;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
/** 入队时捕获的受权候选事实；不把远程接口、凭证或任意SQL交给模型。 */
@Data
public class CampaignGenerationInput implements Serializable {
	private CampaignDraftSaveDTO constraints;
	private List<Candidate> candidates;
	@Data
	public static class Candidate implements Serializable {
		private String skuId;
		private String spuId;
		private String name;
		private BigDecimal salesPrice;
		private BigDecimal costPrice;
		private Integer stock;
		private Integer minOrderQty;
		private Integer stepQty;
		private String salesUnit;
	}
}
