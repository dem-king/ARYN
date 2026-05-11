
package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "评论数量")
public class AppraiseCountVO {

	// 全部
	private Integer allCount;

	// 好评
	private Integer goodCount;

	// 中评
	private Integer badCount;

	// 差评
	private Integer negativeCount;

	// 有图
	private Integer imageCount;

}
