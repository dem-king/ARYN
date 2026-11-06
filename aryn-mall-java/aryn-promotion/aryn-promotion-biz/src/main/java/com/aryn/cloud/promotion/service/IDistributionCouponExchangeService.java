package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.promotion.api.dto.DistributionCouponExchangeDTO;
import com.aryn.cloud.promotion.api.entity.DistributionCouponExchange;
import com.aryn.cloud.promotion.api.vo.DistributionCouponCostVO;
import com.aryn.cloud.promotion.api.vo.DistributionCouponExchangeVO;

import java.time.LocalDateTime;

/**
 * 分销佣金兑换优惠券
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface IDistributionCouponExchangeService {

	/**
	 * 管理端分页（按分销员、券包、状态、创建时间）
	 * @param page 分页参数
	 * @param query 查询条件（userId / packId / status）
	 * @param createTimeStart 创建时间起（可空）
	 * @param createTimeEnd 创建时间止（可空）
	 * @return 兑换记录视图分页
	 */
	IPage<DistributionCouponExchangeVO> getAdminPage(Page<DistributionCouponExchange> page,
			DistributionCouponExchange query, LocalDateTime createTimeStart, LocalDateTime createTimeEnd);

	/**
	 * 券成本汇总（营销费用口径，仅统计已发放成功的记录）
	 * @param query 过滤条件（userId / packId）
	 * @param createTimeStart 创建时间起（可空）
	 * @param createTimeEnd 创建时间止（可空）
	 * @return 兑换笔数、消耗佣金合计、券面额合计
	 */
	DistributionCouponCostVO getStatistics(DistributionCouponExchange query, LocalDateTime createTimeStart,
			LocalDateTime createTimeEnd);

	/**
	 * 小程序：我的兑换记录
	 * @param page 分页参数
	 * @param userId 当前会员ID
	 * @return 兑换记录分页
	 */
	IPage<DistributionCouponExchange> getUserPage(Page<DistributionCouponExchange> page, String userId);

	/**
	 * 佣金兑换优惠券（单事务）：校验券包/库存/可用佣金 → 扣减券包库存与可提现佣金 →
	 * 写佣金支出流水 → 按券包明细逐张发放 coupon_user → 写兑换记录。
	 * 任一步失败抛异常回滚，券发放失败不会扣佣金。
	 * @param userId 当前会员ID
	 * @param dto 券包ID
	 * @return 是否兑换成功
	 */
	Boolean exchange(String userId, DistributionCouponExchangeDTO dto);

}
