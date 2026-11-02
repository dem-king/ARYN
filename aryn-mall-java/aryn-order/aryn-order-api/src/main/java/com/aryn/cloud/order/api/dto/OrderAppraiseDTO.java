
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
	@Min(value = 1, message = "物流评分不能小于1")
	@Max(value = 5, message = "物流评分不能大于5")
	private Integer logisticsScore;

	@Schema(description = "服务评分")
	@Min(value = 1, message = "服务评分不能小于1")
	@Max(value = 5, message = "服务评分不能大于5")
	private Integer serviceScore;

	/**
	 * 文字评价选填（行业口径：星级必填、文字选填），空白时由服务端写入默认文案。
	 */
	@Schema(description = "评论内容（选填，空白时服务端写入默认文案）")
	@Size(max = 255, message = "评论内容不能超过255个字符")
	private String content;

	@Schema(description = "评价标签（最多5个，单个不超过10个字）")
	@Size(max = 5, message = "评价标签不能超过5个")
	private List<@Size(max = 10, message = "单个评价标签不能超过10个字") String> tags;

	@Schema(description = "是否匿名评价：true 匿名展示")
	private Boolean isAnonymous;

}
