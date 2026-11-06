package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.SalesPayoutConfirmDTO;
import com.aryn.cloud.promotion.api.entity.SalesPayout;
import com.aryn.cloud.promotion.api.vo.SalesOrderVO;

import java.util.List;

/**
 * 销售提成月度发放台账服务
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface ISalesPayoutService extends IService<SalesPayout> {

	/**
	 * 管理端台账分页
	 * @param page 分页参数
	 * @param query 查询条件（payoutMonth/salesStaffId/status）
	 * @return 分页结果
	 */
	IPage<SalesPayout> selectAdminPage(Page<SalesPayout> page, SalesPayout query);

	/**
	 * 台账详情
	 * @param id 台账ID
	 * @return 台账
	 */
	SalesPayout getDetail(String id);

	/**
	 * 批次内业绩单分页
	 * @param page 分页参数
	 * @param payoutId 批次ID
	 * @return 分页结果
	 */
	IPage<SalesOrderVO> pageOrders(Page<SalesPayout> page, String payoutId);

	/**
	 * 生成月度台账（幂等：已存在的销售+月份批次跳过）
	 * <p>
	 * 台账月份不得晚于上月，避免把当前未入批订单提前装进未来账期；
	 * 只纳入支付时间早于台账月次月 1 日的可发放业绩单，历史未入批单顺延进本期；
	 * 只有退款负数结转、没有可发放业绩单的销售也会生成台账（允许负实发金额）。
	 * @param payoutMonth 台账月份 yyyy-MM，为空取上月
	 * @return 本次生成的批次数
	 */
	int generate(String payoutMonth);

	/**
	 * 按批次内业绩单重新汇总待确认台账（入批后、退款后调用，防止金额与批次明细不一致）
	 * <p>
	 * 仅对 status=0（待确认）的台账生效；实发金额 = 应发提成 - 上期退款冲减（冲减金额不变）。
	 * @param payoutId 台账ID
	 * @return true=已重算；false=台账不存在或非待确认（不做处理）
	 */
	boolean recalcPendingPayout(String payoutId);

	/**
	 * 财务确认发放（先锁批次内订单并置已发放，再按最新退款情况重算台账，最后台账置已发放）
	 * <p>
	 * 锁序与退款/生成一致（订单 → 台账），避免并发交叉死锁；批次内实际置为已发放的业绩单数
	 * 必须等于重算后的台账单数，否则说明期间有退款/状态变化，抛业务异常回滚本次确认，避免按旧金额发放。
	 * @param dto 请求
	 * @return 是否成功
	 */
	boolean confirm(SalesPayoutConfirmDTO dto);

	/**
	 * 导出台账
	 * @param query 查询条件
	 * @return 台账列表
	 */
	List<SalesPayout> listForExport(SalesPayout query);

}
