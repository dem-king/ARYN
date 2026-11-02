
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 订单评价修改 DTO（评价发布后 30 天内可修改一次）。
 */
@Data
@Schema(description = "订单评价修改DTO")
public class OrderAppraiseEditDTO {

	@Schema(description = "评论内容（选填，不传表示不修改）")
	@Size(max = 255, message = "评论内容不能超过255个字符")
	private String content;

	@Schema(description = "图片（不传表示不修改）")
	@Size(max = 6, message = "评价图片不能超过6张")
	private List<String> picUrls;

	@Schema(description = "商品评分（不传表示不修改）")
	@Min(value = 1, message = "商品评分不能小于1")
	@Max(value = 5, message = "商品评分不能大于5")
	private Integer goodsScore;

}
