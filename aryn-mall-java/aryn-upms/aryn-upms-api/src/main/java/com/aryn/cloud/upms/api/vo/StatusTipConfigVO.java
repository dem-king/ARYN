package com.aryn.cloud.upms.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 空态插画配置（管理端读取 / C 端免登下发共用）。
 *
 * <p>C 端拿到的是租户已配置的完整 URL；某项为空串表示该租户未覆盖，
 * 由前端回落到内置静态图（cloud `/upms/status-tip/{preset}.png`，
 * boot `/boot/status-tip/{preset}.png`）。</p>
 */
@Data
@Schema(description = "空态插画配置")
public class StatusTipConfigVO {

	@Schema(description = "搜索无结果图 URL")
	private String searchUrl;

	@Schema(description = "网络异常图 URL")
	private String networkUrl;

	@Schema(description = "内容为空图 URL")
	private String contentUrl;

	@Schema(description = "收藏为空图 URL")
	private String collectUrl;

	@Schema(description = "评论为空图 URL")
	private String commentUrl;

	@Schema(description = "券/资产为空图 URL")
	private String haloUrl;

	@Schema(description = "消息为空图 URL")
	private String messageUrl;

	@Schema(description = "购物车空态图 URL")
	private String emptyCartUrl;

	@Schema(description = "订单列表空态图 URL")
	private String emptyOrderUrl;

	@Schema(description = "秒杀会场空态图 URL")
	private String emptySeckillUrl;

}
