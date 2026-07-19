
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
	@NotBlank(message = "子订单主键不能为空")
	private String orderItemId;

	@Schema(description = "用户主键")
	private String userId;

	@Schema(description = "头像")
	private String avatarUrl;

	@Schema(description = "昵称")
	private String nickname;

	@Schema(description = "图片")
	@Size(max = 6, message = "评价图片不能超过6张")
	private List<String> picUrls;

	@Schema(description = "商品评分")
	@NotNull(message = "商品评分不能为空")
	@Min(value = 1, message = "商品评分不能小于1")
	@Max(value = 5, message = "商品评分不能大于5")
	private Integer goodsScore;

	@Schema(description = "物流评分")
	private Integer logisticsScore;

	@Schema(description = "服务评分")
	private Integer serviceScore;

	@Schema(description = "评论内容")
	@NotBlank(message = "评论内容不能为空")
	@Size(max = 255, message = "评论内容不能超过255个字符")
	private String content;

}
