
package com.aryn.cloud.product.excel;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.excel.handler.ExcelImportListener;
import com.aryn.cloud.product.api.entity.Brand;
import com.aryn.cloud.product.api.entity.GoodsCategory;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.service.IBrandService;
import com.aryn.cloud.product.service.IGoodsCategoryService;
import com.aryn.cloud.product.service.IGoodsSkuService;
import com.aryn.cloud.product.service.IGoodsSpuService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 商品导入监听器 逐行校验 + 批量入库（500行/批） 导入时支持规格/分类自动创建
 *
 * @author aryn
 * @since 2026/7/5
 */
@Slf4j
public class GoodsSpuImportListener extends ExcelImportListener<GoodsSpuImportDTO> {

	private final IGoodsSpuService goodsSpuService;

	private final IGoodsCategoryService goodsCategoryService;

	private final IBrandService brandService;

	private final IGoodsSkuService goodsSkuService;

	public GoodsSpuImportListener(IGoodsSpuService goodsSpuService, IGoodsCategoryService goodsCategoryService,
			IBrandService brandService, IGoodsSkuService goodsSkuService) {
		this.goodsSpuService = goodsSpuService;
		this.goodsCategoryService = goodsCategoryService;
		this.brandService = brandService;
		this.goodsSkuService = goodsSkuService;
	}

	@Override
	public Class<GoodsSpuImportDTO> getHeadClazz() {
		return GoodsSpuImportDTO.class;
	}

	@Override
	protected String validate(GoodsSpuImportDTO data, int rowIndex) {
		if (StrUtil.isBlank(data.getName())) {
			return "商品名称不能为空";
		}
		if (data.getSalesPrice() == null) {
			return "销售价不能为空";
		}
		if (data.getStock() == null || data.getStock() < 0) {
			return "库存不能为空或小于0";
		}
		return null;
	}

	@Override
	protected void saveBatch(List<GoodsSpuImportDTO> dataList) {
		for (GoodsSpuImportDTO dto : dataList) {
			GoodsSpu spu = new GoodsSpu();
			spu.setName(dto.getName());
			spu.setSubTitle(dto.getSubTitle());
			spu.setSalesPrice(dto.getSalesPrice());
			spu.setOriginalPrice(dto.getOriginalPrice());
			spu.setCostPrice(dto.getCostPrice());
			spu.setStock(dto.getStock());
			spu.setFreightType(dto.getFreightType());
			spu.setFixedFreightPrice(dto.getFixedFreightPrice());
			spu.setDescription(dto.getDescription());
			spu.setEnableSpecs("0");
			spu.setStatus("0");

			// 自动创建或匹配一级类目
			if (StrUtil.isNotBlank(dto.getCategoryFirstName())) {
				spu.setCategoryFirstId(getOrCreateCategory(dto.getCategoryFirstName(), "0"));
			}
			// 自动创建或匹配二级类目
			if (StrUtil.isNotBlank(dto.getCategorySecondName()) && StrUtil.isNotBlank(spu.getCategoryFirstId())) {
				spu.setCategorySecondId(getOrCreateCategory(dto.getCategorySecondName(), spu.getCategoryFirstId()));
			}
			// 自动创建或匹配品牌
			if (StrUtil.isNotBlank(dto.getBrandName())) {
				spu.setBrandId(getOrCreateBrand(dto.getBrandName()));
			}

			goodsSpuService.save(spu);

			// 创建默认SKU
			GoodsSku sku = new GoodsSku();
			sku.setSpuId(spu.getId());
			sku.setSalesPrice(dto.getSalesPrice());
			sku.setOriginalPrice(dto.getOriginalPrice());
			sku.setCostPrice(dto.getCostPrice());
			sku.setStock(dto.getStock());
			sku.setStatus("1");
			goodsSkuService.save(sku);
		}
		log.info("商品导入批量保存, 数量={}", dataList.size());
	}

	/**
	 * 获取或创建类目
	 * @param name 类目名称
	 * @param parentId 父级ID
	 * @return 类目ID
	 */
	private String getOrCreateCategory(String name, String parentId) {
		GoodsCategory existing = goodsCategoryService
			.getOne(Wrappers.<GoodsCategory>lambdaQuery().eq(GoodsCategory::getName, name)
				.eq(GoodsCategory::getParentId, parentId));
		if (existing != null) {
			return existing.getId();
		}
		GoodsCategory category = new GoodsCategory();
		category.setName(name);
		category.setParentId(parentId);
		category.setStatus("0");
		goodsCategoryService.save(category);
		log.info("自动创建类目, name={}, parentId={}", name, parentId);
		return category.getId();
	}

	/**
	 * 获取或创建品牌
	 * @param name 品牌名称
	 * @return 品牌ID
	 */
	private String getOrCreateBrand(String name) {
		Brand existing = brandService.getOne(Wrappers.<Brand>lambdaQuery().eq(Brand::getName, name));
		if (existing != null) {
			return existing.getId();
		}
		Brand brand = new Brand();
		brand.setName(name);
		brand.setStatus("1");
		brandService.save(brand);
		log.info("自动创建品牌, name={}", name);
		return brand.getId();
	}

}