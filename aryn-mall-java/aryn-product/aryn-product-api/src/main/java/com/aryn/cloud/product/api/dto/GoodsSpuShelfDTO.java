
package com.aryn.cloud.product.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Schema(description = "商品上下架")
public class GoodsSpuShelfDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotEmpty(message = "状态不能为空")
	private String status;

	@NotNull(message = "商品不能为空")
	private List<String> spuIds;

}
