package com.aryn.cloud.product.service;

import com.aryn.cloud.product.api.dto.GoodsZoneSkuSnapshotDTO;
import com.aryn.cloud.product.api.dto.GoodsZoneTagBatchDTO;
import com.aryn.cloud.product.api.dto.GoodsZoneTagSaveDTO;
import com.aryn.cloud.product.api.vo.AppClearanceGoodsVO;
import com.aryn.cloud.product.api.vo.AppClearancePriceVO;
import com.aryn.cloud.product.api.vo.GoodsZoneTagResultVO;
import com.aryn.cloud.product.api.vo.GoodsZoneTagVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.product.api.entity.GoodsSpu;

import java.util.List;

/**
 * 商品专区标签：会员/出清标签的批量打标、单商品精调与专区选品。
 *
 * @author aryn
 * @since 2026/11/03
 */
public interface IGoodsZoneTagService {

	List<GoodsZoneTagResultVO> batchSave(GoodsZoneTagBatchDTO dto);

	GoodsZoneTagVO getTags(String spuId);

	void saveTags(String spuId, GoodsZoneTagSaveDTO dto);

	List<GoodsZoneSkuSnapshotDTO> listZoneSkus();

	AppClearanceGoodsVO clearanceGoods(long limit);

	/** 批量查询 SKU 的有效出清价（商详/购物车展示，最多 200 个）。 */
	AppClearancePriceVO clearancePrices(List<String> skuIds);

	/**
	 * 批量查询 SPU 下各 SKU 的有效出清价（商品列表卡片展示，最多 200 个 SPU）。
	 *
	 * <p>列表卡片只有 SPU（无 SKU 明细），后端按与商详/结算同一套口径算出每个有效 SKU 的出清价
	 * 并回填 spuId，由 C 端按卡片展示价聚合最低有效价；未打标/到期/倒挂的 SKU 不返回。
	 */
	AppClearancePriceVO clearanceSpuPrices(List<String> spuIds);

	/** 出清专区分页：标签 + 分类筛选 + 系统活动最终价 + 专区排序，关闭专区返回空页。 */
	IPage<GoodsSpu> clearancePage(Page page, GoodsSpu query, Boolean memberItem);

	/** 专区是否开启（member / clearance），未配置按开启处理。 */
	boolean zoneEnabled(String zoneType);

}
