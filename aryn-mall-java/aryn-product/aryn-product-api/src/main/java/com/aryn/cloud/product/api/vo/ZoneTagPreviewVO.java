package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量专区操作预检结果。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "批量专区操作预检结果")
public class ZoneTagPreviewVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "总体汇总")
	private Summary summary;

	@Schema(description = "分专区汇总")
	private List<Zone> zones;

	@Schema(description = "逐商品状态")
	private List<ZoneTagItemStateVO> items;

	@Data
	@Schema(description = "预检总体汇总")
	public static class Summary implements Serializable {

		private static final long serialVersionUID = 1L;

		private Integer total;
		private Integer add;
		private Integer update;
		private Integer noChange;
		private Integer remove;
		private Integer invalid;
		private Integer warningCount;
		private Integer skuOverridesWillClear;

	}

	@Data
	@Schema(description = "预检分区汇总")
	public static class Zone implements Serializable {

		private static final long serialVersionUID = 1L;

		private String zone;
		private Integer existing;
		private Integer willAdd;
		private Integer willUpdate;
		private Integer willRemove;
		private Integer noChange;
		private Integer withSkuOverride;
		private Integer invalid;

	}

}
