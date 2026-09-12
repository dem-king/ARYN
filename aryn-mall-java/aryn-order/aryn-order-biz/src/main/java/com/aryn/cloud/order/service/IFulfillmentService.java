package com.aryn.cloud.order.service;

import com.aryn.cloud.order.api.dto.FulfillmentExceptionDTO;
import com.aryn.cloud.order.api.dto.FulfillmentPickScanDTO;
import com.aryn.cloud.order.api.dto.FulfillmentShortReportDTO;
import com.aryn.cloud.order.api.dto.FulfillmentWaveCreateDTO;
import com.aryn.cloud.order.api.entity.FulfillmentException;
import com.aryn.cloud.order.api.entity.FulfillmentPickItem;
import com.aryn.cloud.order.api.entity.FulfillmentWave;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 仓库履约服务：拣货波次、扫码拣货、短装和异常。
 *
 * <p>第一期只做订单分组、SKU/数量校验、交接和异常证据，不做库位与补货算法。
 *
 * @author aryn
 * @since 2026/9/12
 */
public interface IFulfillmentService {

	/**
	 * 创建拣货波次（同港口、相近时间窗订单合并作业）。
	 */
	FulfillmentWave createWave(String tenantId, String operatorId, String operatorName,
			FulfillmentWaveCreateDTO dto);

	/**
	 * 将订单加入波次并按订单明细生成拣货明细；订单必须与波次同港口。
	 */
	List<FulfillmentPickItem> addOrderToWave(String tenantId, String waveId, String orderId);

	/**
	 * 扫码拣货：校验 SKU 与数量，全部拣完自动进入已复核前置状态。
	 */
	FulfillmentPickItem scanPick(String tenantId, String pickerId, FulfillmentPickScanDTO dto);

	/**
	 * 报告短装：必须记录原因和实际数量。
	 */
	FulfillmentPickItem reportShort(String tenantId, String pickerId, FulfillmentShortReportDTO dto);

	/**
	 * 复核波次：全部明细已拣/短装后复核通过。
	 */
	FulfillmentWave reviewWave(String tenantId, String operatorId, String operatorName, String waveId);

	/**
	 * 交接司机：复核通过后交接，交接后仓库不能继续修改明细。
	 */
	FulfillmentWave handOverToDriver(String tenantId, String operatorId, String operatorName, String waveId,
			String staffId);

	/**
	 * 上报履约异常（含证据）。
	 */
	FulfillmentException reportException(String tenantId, String reporterId, FulfillmentExceptionDTO dto);

	/**
	 * 关闭异常。
	 */
	FulfillmentException closeException(String tenantId, String handlerId, String exceptionId, String handleRemark);

	/**
	 * 波次分页（按计划交付时间排序）。
	 */
	IPage<FulfillmentWave> wavePage(String tenantId, IPage<FulfillmentWave> page, FulfillmentWave query);

	/**
	 * 波次拣货明细。
	 */
	List<FulfillmentPickItem> listWaveItems(String tenantId, String waveId);

	/**
	 * 异常分页。
	 */
	IPage<FulfillmentException> exceptionPage(String tenantId, IPage<FulfillmentException> page,
			FulfillmentException query);

	/**
	 * 港口配送看板：按港口聚合当日订单/波次/配送任务状态。
	 */
	java.util.Map<String, Object> portBoard(String tenantId, String portCode, java.time.LocalDate date);

	/**
	 * 靠港计划变更影响面：未完成订单、共享购物车与波次聚合（供管理端 ETA 变更前评估）。
	 */
	java.util.Map<String, Object> vesselCallImpact(String tenantId, String vesselCallId);

}
