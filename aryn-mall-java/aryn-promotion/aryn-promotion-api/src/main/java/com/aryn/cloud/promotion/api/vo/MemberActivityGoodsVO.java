package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员专区自动数据源：按会员价活动（promotion_activity type=10）自动取商品。
 *
 * <p>装修多 Tab 商品流新增 {@code dataSource.mode="activity"} 后，运营不再手选商品：
 * 专区商品 = 活动配置的 SKU 对应 SPU，顺序 = 活动 {@code rules.skus} 配置顺序，
 * 活动加减商品后 C 端自动同步。
 *
 * <p>接口免登（可选登录无差别）：只返回活动元信息与 SPU ID 列表，不含用户维度数据；
 * 具体价格仍由 {@code /app/member/prices} 按 SPU/SKU 查询（未登录返回活动配置价），
 * 不在本接口混入价格，避免与价格聚合口径重复。
 *
 * @author aryn
 * @since 2026/10/25
 */
@Data
@Schema(description = "会员专区自动数据源：会员价活动商品（SPU 列表）")
public class MemberActivityGoodsVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "会员价活动ID")
	private String activityId;

	@Schema(description = "活动名称（专区标题/运营核对用）")
	private String activityName;

	@Schema(description = "活动结束时间（C端倒计时/过期提示用）")
	private LocalDateTime endTime;

	@Schema(description = "活动配置商品对应的 SPU ID 列表（按活动 SKU 配置顺序去重保序；同一 SPU 多规格只保留首次出现位置）")
	private List<String> spuIds;

}
