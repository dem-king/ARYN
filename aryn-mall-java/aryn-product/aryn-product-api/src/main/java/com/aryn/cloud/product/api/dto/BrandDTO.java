
package com.aryn.cloud.product.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 品牌数据传输对象
 *
 * @author aryn
 * @since 2026/7/5
 */
@Data
@Schema(description = "品牌DTO")
public class BrandDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotBlank(message = "品牌名称不能为空")
	@Schema(description = "品牌名称")
	private String name;

	@Schema(description = "品牌Logo")
	private String logo;

	@Schema(description = "排序")
	private Integer sort;

	@Schema(description = "首字母")
	private String firstLetter;

	@Schema(description = "状态(0禁用1启用)")
	private String status;

}