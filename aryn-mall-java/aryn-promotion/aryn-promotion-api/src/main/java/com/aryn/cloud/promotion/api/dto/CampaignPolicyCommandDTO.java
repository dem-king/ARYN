package com.aryn.cloud.promotion.api.dto;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
/** 协议价显式命令；不接受租户、发布状态或任意适用主体。 */
@Data
public class CampaignPolicyCommandDTO implements Serializable {
	private Long expectedRevision;
	private String name;
	private Integer priority;
	private List<Item> items;
	@Data public static class Item implements Serializable {
		private String skuId;
		private BigDecimal price;
		private Integer minQty;
	}
}
