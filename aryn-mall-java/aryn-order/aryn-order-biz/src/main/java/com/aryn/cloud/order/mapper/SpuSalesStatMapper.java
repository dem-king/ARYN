package com.aryn.cloud.order.mapper;

import com.aryn.cloud.order.api.dto.GoodsSpuSalesStatDTO;
import com.aryn.cloud.order.api.entity.StatDailySpuSales;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * SPU 社证统计 Mapper（T+1 聚合写入与最新期读取）。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Mapper
public interface SpuSalesStatMapper extends BaseMapper<StatDailySpuSales> {

	/**
	 * 聚合写入指定租户、指定快照日的近 days 天社证统计。
	 *
	 * <p>口径与热销榜/常购清单同源：order_info.status IN ('2','3','4')、双方未删除、
	 * 窗口按 order_info.create_time。uk (tenant_id, spu_id, stat_date) 冲突走覆盖，可重复执行。
	 *
	 * @return 影响行数（含覆盖更新）
	 */
	int upsertSpuStats(@Param("tenantId") String tenantId, @Param("statDate") LocalDate statDate,
			@Param("days") int days);

	/**
	 * 取各 SPU 的最新一期统计（每 SPU 取 stat_date 最大且不晚于今日的行）。
	 */
	List<GoodsSpuSalesStatDTO> selectLatestStats(@Param("tenantId") String tenantId,
			@Param("spuIds") List<String> spuIds);

}
