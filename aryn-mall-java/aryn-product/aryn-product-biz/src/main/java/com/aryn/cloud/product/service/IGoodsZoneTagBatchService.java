package com.aryn.cloud.product.service;

import com.aryn.cloud.product.api.dto.GoodsZoneTagBatchDTO;
import com.aryn.cloud.product.api.dto.GoodsZoneTagQueryDTO;
import com.aryn.cloud.product.api.vo.ZoneTagBatchResultVO;
import com.aryn.cloud.product.api.vo.ZoneTagItemStateVO;
import com.aryn.cloud.product.api.vo.ZoneTagPreviewVO;

import java.util.List;

/**
 * 专区标签批量操作：预检、执行、批量状态查询。
 *
 * <p>预检与执行共用同一套变更判定与校验，保证「预检看到的影响 = 实际执行结果」。
 *
 * @author aryn
 * @since 2026/11/04
 */
public interface IGoodsZoneTagBatchService {

	/**
	 * 批量预检：只读，返回当前状态、拟变更类型、影响范围与告警。
	 */
	ZoneTagPreviewVO preview(GoodsZoneTagBatchDTO dto);

	/**
	 * 批量执行：落库标签/规则，写价格历史与批次记录，返回逐条结果与同步状态。
	 */
	ZoneTagBatchResultVO execute(GoodsZoneTagBatchDTO dto);

	/**
	 * 批量读取当前专区状态（含 SKU 覆盖明细）。
	 */
	List<ZoneTagItemStateVO> query(GoodsZoneTagQueryDTO dto);

}
