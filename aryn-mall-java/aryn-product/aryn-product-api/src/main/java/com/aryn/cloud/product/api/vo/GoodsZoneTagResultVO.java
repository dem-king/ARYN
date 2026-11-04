package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 批量打标逐条结果。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批量打标逐条结果")
public class GoodsZoneTagResultVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String spuId;

	private String spuName;

	private boolean success;

	private String message;

	public static GoodsZoneTagResultVO ok(String spuId, String spuName) {
		return new GoodsZoneTagResultVO(spuId, spuName, true, "OK");
	}

	public static GoodsZoneTagResultVO fail(String spuId, String spuName, String message) {
		return new GoodsZoneTagResultVO(spuId, spuName, false, message);
	}

}
