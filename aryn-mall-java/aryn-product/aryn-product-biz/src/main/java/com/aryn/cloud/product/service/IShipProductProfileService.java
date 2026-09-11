package com.aryn.cloud.product.service;

import com.aryn.cloud.product.api.dto.ShipProductProfileDTO;
import com.aryn.cloud.product.api.entity.ProductCodeMapping;
import com.aryn.cloud.product.api.entity.ShipGoodsProfile;
import com.aryn.cloud.product.api.entity.ShipSkuProfile;
import com.aryn.cloud.product.api.vo.ShipProductSummaryVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 船供商品资料服务。
 *
 * @author aryn
 * @since 2026/9/11
 */
public interface IShipProductProfileService {

	/**
	 * 保存 SPU 船供资料、SKU 包装资料与编码映射（同事务）。
	 */
	ShipGoodsProfile saveProfile(String tenantId, ShipProductProfileDTO dto);

	/**
	 * 查询 SPU 船供资料。
	 */
	ShipGoodsProfile getProfile(String tenantId, String spuId);

	/**
	 * 查询 SPU 下 SKU 包装资料。
	 */
	List<ShipSkuProfile> listSkuProfiles(String tenantId, String spuId);

	/**
	 * 查询 SPU 下编码映射。
	 */
	List<ProductCodeMapping> listCodeMappings(String tenantId, String spuId);

	/**
	 * 船供目录分页（sale_scope 2/3）。
	 */
	IPage<ShipProductSummaryVO> shipSummaryPage(String tenantId, IPage<ShipProductSummaryVO> page,
			ShipProductSummaryVO query);

	/**
	 * 个人商品分页（sale_scope 1/3）。
	 */
	IPage<ShipProductSummaryVO> personalSummaryPage(String tenantId, IPage<ShipProductSummaryVO> page,
			ShipProductSummaryVO query);

}
