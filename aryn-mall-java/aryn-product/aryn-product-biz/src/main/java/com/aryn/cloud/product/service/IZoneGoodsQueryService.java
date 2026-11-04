package com.aryn.cloud.product.service;

import com.aryn.cloud.product.api.dto.ZoneGoodsIdsDTO;
import com.aryn.cloud.product.api.dto.ZoneGoodsQueryDTO;
import com.aryn.cloud.product.api.vo.ZoneGoodsIdsVO;
import com.aryn.cloud.product.api.vo.ZoneGoodsRowVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Collection;
import java.util.Map;

/**
 * 专区商品管理查询：专区商品分页、按筛选取 ID、SKU 精调计数。
 *
 * @author aryn
 * @since 2026/11/04
 */
public interface IZoneGoodsQueryService {

	/**
	 * 专区商品分页（固定 ORDER BY id ASC），带生效状态与 SKU 精调计数。
	 */
	IPage<ZoneGoodsRowVO> page(ZoneGoodsQueryDTO query);

	/**
	 * 按筛选条件返回全部 SPU ID（上限 1000），供「全选筛选结果 / 换一批」。
	 */
	ZoneGoodsIdsVO ids(ZoneGoodsIdsDTO query);

	/**
	 * 批量统计 SPU 下存在专区规则的 SKU 数（用于列表/预览）。
	 */
	Map<String, Integer> skuOverrideCounts(Collection<String> spuIds);

}
