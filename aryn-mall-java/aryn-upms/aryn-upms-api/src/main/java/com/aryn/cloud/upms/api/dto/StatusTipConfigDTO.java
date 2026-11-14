package com.aryn.cloud.upms.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 空态插画配置保存入参。
 *
 * <p>只接受 10 个可编辑的图片地址（7 个 wd-status-tip 预设位 + 3 张会场空态插画）；
 * 主键、租户归属与审计列由服务端决定。
 * 传空串表示清除该项，回落到随包发布的内置静态图，因此保存必须全字段覆盖，
 * 不能用「非空才更新」的实体直更策略。</p>
 */
@Data
@Schema(description = "空态插画配置保存入参")
public class StatusTipConfigDTO {

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
