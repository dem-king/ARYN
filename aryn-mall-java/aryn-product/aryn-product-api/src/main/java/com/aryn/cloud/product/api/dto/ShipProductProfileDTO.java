package com.aryn.cloud.product.api.dto;

import com.aryn.cloud.product.api.entity.ProductCodeMapping;
import com.aryn.cloud.product.api.entity.ShipGoodsProfile;
import com.aryn.cloud.product.api.entity.ShipSkuProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * SPU 船供资料保存 DTO。
 *
 * <p>SPU 船供资料、SKU 包装资料与编码映射在同一事务中保存。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@Schema(description = "SPU船供资料保存DTO")
public class ShipProductProfileDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "商品SPU ID")
	@NotBlank(message = "商品SPU ID不能为空")
	private String spuId;

	@Schema(description = "SPU 船供资料")
	@Valid
	private ShipGoodsProfile profile;

	@Schema(description = "SKU 包装资料列表")
	@Valid
	private List<ShipSkuProfile> skuProfiles;

	@Schema(description = "编码映射列表")
	@Valid
	private List<ProductCodeMapping> codeMappings;

}
