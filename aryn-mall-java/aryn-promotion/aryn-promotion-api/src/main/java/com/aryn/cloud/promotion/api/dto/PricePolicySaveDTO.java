package com.aryn.cloud.promotion.api.dto;

import com.aryn.cloud.promotion.api.entity.PricePolicy;
import com.aryn.cloud.promotion.api.entity.PricePolicyItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 价格政策保存入参（管理端新建/修改）。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "价格政策保存DTO")
public class PricePolicySaveDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "政策基础信息")
	private PricePolicy policy;

	@Schema(description = "SKU 明细（全量覆盖，按唯一键 upsert）")
	private List<PricePolicyItem> items;

}
