
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "订单评价DTO")
public class OrderAppraiseDTO {

	@Schema(description = "spuId")
	private String spuId;

	@Schema(description = "订单主键")
	private String orderId;

	@Schema(description = "子订单主键")
	private String orderItemId;

	@Schema(description = "用户主键")
	private String userId;

	@Schema(description = "头像")
	private String avatarUrl;

	@Schema(description = "昵称")
	private String nickname;

	@Schema(description = "图片")
	private List<String> picUrls;

	@Schema(description = "商品评分")
	private Integer goodsScore;

	@Schema(description = "物流评分")
	private Integer logisticsScore;

	@Schema(description = "服务评分")
	private Integer serviceScore;

	@Schema(description = "评论内容")
	private String content;

}
