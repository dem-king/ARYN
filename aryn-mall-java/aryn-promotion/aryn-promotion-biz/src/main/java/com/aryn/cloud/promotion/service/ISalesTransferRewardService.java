package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.SalesTransferReward;
import com.aryn.cloud.promotion.api.vo.SalesTransferRewardVO;

import java.math.BigDecimal;

/**
 * 客户转交奖励服务
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface ISalesTransferRewardService extends IService<SalesTransferReward> {

	/**
	 * 管理端转交奖励分页
	 * @param page 分页参数
	 * @param query 查询条件（customerUserId/salesStaffId/status）
	 * @return 分页结果
	 */
	IPage<SalesTransferRewardVO> selectAdminPage(Page<SalesTransferReward> page, SalesTransferReward query);

	/**
	 * 小程序「我的转交奖励」分页（分销员视角）
	 * @param page 分页参数
	 * @param distributorUserId 分销员会员ID
	 * @return 分页结果
	 */
	IPage<SalesTransferRewardVO> selectAppPage(Page<SalesTransferReward> page, String distributorUserId);

	/**
	 * 生成转交奖励并入账到原分销员待结算佣金（按 customer+bizOrderId 幂等）
	 * @param customerUserId 客户会员ID
	 * @param distributorUserId 原分销员会员ID
	 * @param salesStaffId 接收销售ID
	 * @param bizOrderId 触发订单ID
	 * @param firstOrderAmount 首单实付（不含运费）
	 * @param rewardRate 奖励比例快照
	 * @return true=本次完成入账，false=重复/金额为0/分销员不存在
	 */
	boolean grantReward(String customerUserId, String distributorUserId, String salesStaffId, String bizOrderId,
			BigDecimal firstOrderAmount, BigDecimal rewardRate);

	/**
	 * 按分销结算周期释放待结算的转交奖励：待结算佣金 → 可提现佣金（status 0 → 1）
	 * <p>
	 * 结算周期取 {@code distribution_config.settle_cycle_days}（空按 7、负数按 0），与分销订单释放口径一致。
	 * 单条奖励在独立新事务中释放（先锁 reward 行、后锁 distributor 行），单条失败只记日志不影响其他条。
	 * @return 本次释放条数
	 */
	int settlePendingRewards();

	/**
	 * 首单全额退款时扣回转交奖励（status 0/1 → 2，按 bizOrderId 幂等）
	 * @param bizOrderId 触发转交奖励的业务订单ID
	 * @return true=本次完成扣回，false=无奖励/已扣回/原分销员不存在
	 */
	boolean clawbackReward(String bizOrderId);

}
