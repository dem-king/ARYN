package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.SalesCustomerClaim;
import com.aryn.cloud.promotion.api.vo.SalesCustomerClaimVO;

/**
 * 客户待转交认领服务
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface ISalesCustomerClaimService extends IService<SalesCustomerClaim> {

	/**
	 * 管理端认领分页
	 * @param page 分页参数
	 * @param query 查询条件（status/salesStaffId）
	 * @return 分页结果
	 */
	IPage<SalesCustomerClaimVO> selectAdminPage(Page<SalesCustomerClaim> page, SalesCustomerClaim query);

	/**
	 * 查询客户对该销售的未过期待首单认领
	 * @param customerUserId 客户会员ID
	 * @param salesStaffId 销售ID
	 * @return 认领；不存在或已过期/已处理返回 null
	 */
	SalesCustomerClaim getPendingClaim(String customerUserId, String salesStaffId);

	/**
	 * 写入/替换授权认领（同一客户只保留一条，重复点击更新销售/来源/时间并重置有效期）
	 * @param customerUserId 客户会员ID
	 * @param salesStaffId 销售ID
	 * @param source 来源 SHARE/QRCODE
	 * @return 认领记录
	 */
	SalesCustomerClaim upsertClaim(String customerUserId, String salesStaffId, String source);

	/**
	 * 认领置为已转交
	 * @param claimId 认领ID
	 * @return 是否成功
	 */
	boolean markTransferred(String claimId);

	/**
	 * 作废客户未完成认领
	 * @param customerUserId 客户会员ID
	 * @return 作废条数
	 */
	int invalidatePendingClaims(String customerUserId);

	/**
	 * 作废销售名下未完成认领（离职交接）
	 * @param salesStaffId 销售ID
	 * @return 作废条数
	 */
	int invalidatePendingClaimsByStaff(String salesStaffId);

	/**
	 * 认领过期置为失效（定时任务）
	 * @return 处理条数
	 */
	int expireClaims();

}
