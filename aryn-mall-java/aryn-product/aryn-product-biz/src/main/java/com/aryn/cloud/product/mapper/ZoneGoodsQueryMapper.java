package com.aryn.cloud.product.mapper;

import com.aryn.cloud.product.api.dto.ZoneGoodsIdsDTO;
import com.aryn.cloud.product.api.dto.ZoneGoodsQueryDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 专区商品管理查询 Mapper（独立 XML，不并入 GoodsSpuMapper）。
 *
 * <p>固定 {@code ORDER BY goods_spu.id ASC}，保证分页遍历稳定（G16：原
 * selectPageByAdmin 无 ORDER BY，翻页收集 ID 可能漏/重）。
 *
 * <p>租户隔离由 MyBatis 租户拦截器自动注入，SQL 不显式拼 tenant_id。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Mapper
public interface ZoneGoodsQueryMapper {

	/**
	 * 专区商品分页（member/clearance + 名称/类目/品牌/状态筛选），固定 id 升序。
	 */
	IPage<GoodsSpu> selectZonePage(Page<GoodsSpu> page, @Param("query") ZoneGoodsQueryDTO query);

	/**
	 * 按筛选条件取商品 ID（zoneType 支持 member/clearance/none/not_member/not_clearance）。
	 */
	List<String> selectZoneIds(@Param("query") ZoneGoodsIdsDTO query, @Param("limit") int limit);

	/**
	 * 按筛选条件计数（与 {@link #selectZoneIds} 同一 where 口径）。
	 */
	long countZoneIds(@Param("query") ZoneGoodsIdsDTO query);

	/**
	 * 批量读取 SPU 下全部 SKU 的专区覆盖原值（null=继承 SPU）。
	 *
	 * <p>用于：专区商品页 skuOverrideCount / ids 场景 SKU 精调计数 / 批次撤销的冲突比较与回滚。
	 */
	List<GoodsSku> selectSkuZoneRows(@Param("spuIds") Collection<String> spuIds);

}
