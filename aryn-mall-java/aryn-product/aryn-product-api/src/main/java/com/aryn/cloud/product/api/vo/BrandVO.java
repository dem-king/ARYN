
package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 品牌视图对象
 *
 * @author aryn
 * @since 2026/7/5
 */
@Data
@Schema(description = "品牌VO")
public class BrandVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "品牌ID")
	private String id;

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

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

}