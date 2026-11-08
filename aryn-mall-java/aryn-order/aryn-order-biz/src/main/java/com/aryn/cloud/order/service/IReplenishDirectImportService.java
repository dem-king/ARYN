package com.aryn.cloud.order.service;

import com.aryn.cloud.order.api.dto.ReplenishDirectImportConfirmDTO;
import com.aryn.cloud.order.api.vo.ReplenishDirectImportConfirmVO;
import com.aryn.cloud.order.api.vo.ReplenishDirectImportVO;

/**
 * 清单直下单导入：解析 → 报告 → 确认产出已复核 SKU 清单。
 *
 * <p>「导入清单下单」向导的不挂车链路：任务只承载「解析 → 人工处置 → 复核出单」，
 * 确认的终点是返回给结算页的明细清单（价格/库存由结算与下单服务端重算），
 * 不写任何购物车明细、不生成订单。
 *
 * @author aryn
 * @since 2026/11/7
 */
public interface IReplenishDirectImportService {

	/**
	 * 上传 Excel，解析并返回导入报告（任务 + 行明细落库）。
	 */
	ReplenishDirectImportVO preview(String tenantId, String userId, String fileName, long fileSize,
			byte[] fileBytes);

	/**
	 * 取回导入报告（稍后处理）。
	 */
	ReplenishDirectImportVO getImport(String tenantId, String userId, String importId);

	/**
	 * 最近一次待确认的导入报告；没有进行中的任务返回 null。
	 */
	ReplenishDirectImportVO findPending(String tenantId, String userId);

	/**
	 * 最近一次可继续的导入（待确认或已确认）；用于杀进程/重启后恢复。
	 * 已取消的任务不返回。没有则返回 null。
	 */
	ReplenishDirectImportVO findLatest(String tenantId, String userId);

	/**
	 * 恢复已确认导入的复核清单（只读，不重新确认、不改写解析行）。
	 *
	 * <p>确认成功但用户未到结算页就退出/杀进程时，靠本方法从已确认任务
	 * 重建同一份合并清单重新进入结算；待确认任务返回不可结算回执，
	 * 引导回报告页继续处置。
	 */
	ReplenishDirectImportConfirmVO resume(String tenantId, String userId, String importId);

	/**
	 * 确认导入：按「行号 → 处置动作」重新校验（数量/SKU 以服务端解析行为准），
	 * 返回同 SKU 合并后的已复核明细清单及真实的跳过行。幂等。
	 */
	ReplenishDirectImportConfirmVO confirm(String tenantId, String userId, String importId,
			ReplenishDirectImportConfirmDTO dto);

}
