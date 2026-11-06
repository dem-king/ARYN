package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.SalesOrderQueryDTO;
import com.aryn.cloud.promotion.api.entity.SalesOrder;
import com.aryn.cloud.promotion.api.vo.AppSalesOrderVO;
import com.aryn.cloud.promotion.api.vo.SalesOrderVO;
import com.aryn.cloud.promotion.api.vo.SalesReportVO;
import com.aryn.cloud.promotion.api.vo.SalesTrendVO;
import com.aryn.cloud.promotion.vo.SalesOrderStatisticsVO;
import com.aryn.cloud.promotion.vo.SalesPayoutAggregateVO;
import com.aryn.cloud.promotion.vo.SalesWorkbenchStatsVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售业绩单服务
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface ISalesOrderService extends IService<SalesOrder> {

	/**
	 * 管理端业绩单分页
	 * @param page 分页参数
	 * @param query 查询条件
	 * @return 分页结果
	 */
	IPage<SalesOrderVO> selectAdminPage(Page<SalesOrder> page, SalesOrderQueryDTO query);

	/**
	 * 导出查询
	 * @param query 查询条件
	 * @return 业绩单列表
	 */
	List<SalesOrder> listForExport(SalesOrderQueryDTO query);

	/**
	 * 汇总卡片
	 * @param query 查询条件
	 * @return 汇总
	 */
	SalesOrderStatisticsVO statistics(SalesOrderQueryDTO query);

	/**
	 * 业绩报表（按销售汇总）
	 * @param payoutMonth 月份 yyyy-MM，可空=全部
	 * @param salesStaffId 销售ID，可空
	 * @param deptId 部门ID，可空
	 * @return 报表行
	 */
	List<SalesReportVO> reportSummary(String payoutMonth, String salesStaffId, String deptId);

	/**
	 * 业绩趋势（按月，缺失月份补 0）
	 * @param salesStaffId 销售ID，可空
	 * @param months 最近月份数
	 * @return 趋势行
	 */
	List<SalesTrendVO> reportTrend(String salesStaffId, int months);

	/**
	 * 小程序业绩明细分页
	 * @param page 分页参数
	 * @param salesStaffId 销售ID
	 * @return 分页结果
	 */
	IPage<AppSalesOrderVO> selectAppPage(Page<SalesOrder> page, String salesStaffId);

	/**
	 * 按业务订单查业绩单
	 * @param bizOrderId 业务订单ID
	 * @return 业绩单，无则 null
	 */
	SalesOrder getByBizOrderId(String bizOrderId);

	/**
	 * 幂等保存业绩单（唯一键冲突返回 false）
	 * @param order 业绩单
	 * @return true=本次创建成功
	 */
	boolean saveIdempotent(SalesOrder order);

	/**
	 * 聚合待发放业绩单（status=1、payout_id 为空且支付时间早于台账月次月 1 日），跨月未发放自然顺延
	 * @param payTimeBefore 支付时间上限（不含），台账月次月 1 日 0 点，避免把台账月之后的订单装进本期
	 * @return 按销售聚合行
	 */
	List<SalesPayoutAggregateVO> aggregatePayoutOrders(LocalDateTime payTimeBefore);

	/**
	 * 是否存在尚未入批的可发放业绩单（用于定时任务漏跑补跑判断）
	 * @param payTimeBefore 支付时间上限（不含）
	 * @return true=存在待入批业绩单
	 */
	boolean existsUnbatchedPayoutOrders(LocalDateTime payTimeBefore);

	/**
	 * 按批次重新汇总业绩单（退款后重算待确认台账用）
	 * @param payoutId 批次ID
	 * @return 汇总行（单数不含已全额退款；成交额/提成为扣减退款后的净额）
	 */
	SalesPayoutAggregateVO summarizePayoutOrders(String payoutId);

	/**
	 * 把销售的待发放业绩单纳入批次（只纳入支付时间早于台账月次月 1 日的单）
	 * @param salesStaffId 销售ID
	 * @param payoutId 批次ID
	 * @param payTimeBefore 支付时间上限（不含）
	 * @return 更新条数
	 */
	int assignPayout(String salesStaffId, String payoutId, LocalDateTime payTimeBefore);

	/**
	 * 批次内业绩单分页
	 * @param page 分页参数
	 * @param payoutId 批次ID
	 * @return 分页结果
	 */
	IPage<SalesOrderVO> selectPayoutOrderPage(Page<SalesOrder> page, String payoutId);

	/**
	 * 确认发放联动：批次内业绩单置为已发放（status 1 -> 2）
	 * @param payoutId 批次ID
	 * @param settleTime 发放时间
	 * @return 更新条数
	 */
	int markPayoutPaid(String payoutId, LocalDateTime settleTime);

	/**
	 * 小程序工作台业绩统计
	 * @param salesStaffId 销售ID
	 * @return 统计
	 */
	SalesWorkbenchStatsVO workbenchStats(String salesStaffId);

}
