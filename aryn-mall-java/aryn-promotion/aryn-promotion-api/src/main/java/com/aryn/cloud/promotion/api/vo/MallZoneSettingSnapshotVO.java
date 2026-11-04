package com.aryn.cloud.promotion.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 专区设置快照：供 C 端与商品域读取（不暴露内部 id/租户字段）。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Data
public class MallZoneSettingSnapshotVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String zoneType;

	private boolean enabled;

	private BigDecimal defaultDiscountRate;

	/** 会员价成本兜底倍率：下限=成本×倍率；空=不启用 */
	private BigDecimal costFloorMultiplier;

	private String sortMode;

}
