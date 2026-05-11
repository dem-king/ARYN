
package com.aryn.cloud.product.api.vo;

import com.aryn.cloud.product.api.entity.GoodsSpu;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "用户收藏VO")
public class GoodsCollectVO {

	@Schema(description = "主键")
	private String id;

	@Schema(description = "用户主键")
	private String userId;

	@Schema(description = "商品id")
	private String spuId;

	@Schema(description = "加入时价格（元）")
	private BigDecimal salesPrice;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "商品信息")
	private GoodsSpu goodsSpu;

}
