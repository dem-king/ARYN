package com.aryn.cloud.product.mapper;

import com.aryn.cloud.product.api.entity.GoodsSkuPriceDaily;
import com.aryn.cloud.product.support.SkuPriceWindowStat;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * SKU 每日价格快照 Mapper（快照写入与近 N 天窗口聚合）。
 *
 * <p>两条语句 SQL 均显式带 tenant_id 条件，租户由拦截器（表已登记 hx.tenant.tables）
 * 与显式条件双重保证；uk (tenant_id, sku_id, stat_date) 冲突走覆盖，Job 重跑幂等。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Mapper
public interface GoodsSkuPriceDailyMapper extends BaseMapper<GoodsSkuPriceDaily> {

	/**
	 * 批量写入当日快照（撞 uk 覆盖 sale_price，可重复执行）。
	 *
	 * @return 影响行数（含覆盖更新）
	 */
	int upsertDailyPrices(@Param("rows") List<GoodsSkuPriceDaily> rows);

	/**
	 * 按页级 SKU 批量聚合近 N 天价格窗口（最低价/变价次数/当日价）。
	 */
	List<SkuPriceWindowStat> selectWindowStats(@Param("tenantId") String tenantId,
			@Param("skuIds") List<String> skuIds, @Param("startDate") LocalDate startDate,
			@Param("today") LocalDate today);

}
