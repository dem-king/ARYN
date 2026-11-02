
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 订单追评 DTO（评价发布后 30 天内可追评一次）。
 */
@Data
@Schema(description = "订单追评DTO")
public class OrderAdvanceAppraiseDTO {

	@Schema(description = "子订单主键")
	@NotBlank(message = "子订单主键不能为空")
	private String orderItemId;

	@Schema(description = "追评内容（选填，空白时服务端写入默认文案）")
	@Size(max = 255, message = "追评内容不能超过255个字符")
	private String content;

	@Schema(description = "追评图片")
	@Size(max = 6, message = "追评图片不能超过6张")
	private List<String> picUrls;

}
