package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.promotion.api.dto.DistributionUserRegisterDTO;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBind;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
import com.aryn.cloud.promotion.api.enums.SalesCustomerBindSourceEnum;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import com.aryn.cloud.promotion.service.ISalesCustomerBindFacade;
import com.aryn.cloud.promotion.service.ISalesCustomerBindService;
import com.aryn.cloud.promotion.service.ISalesCustomerClaimService;
import com.aryn.cloud.promotion.service.ISalesStaffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分享/扫码绑定分流（IMPL 4.1）
 * <p>
 * 邀请人是<b>在职销售</b>时走销售归属：直接绑定 / 已归属分销员则写待转交认领；
 * 邀请人不是销售时保持原分销注册逻辑，但客户已正式归属销售后不再建立新的分销上级关系（需求 §4.2）。
 * 任何分支都不把销售会员账号写成客户的分销上级。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesCustomerBindFacade implements ISalesCustomerBindFacade {

	private final ISalesStaffService salesStaffService;

	private final ISalesCustomerBindService salesCustomerBindService;

	private final ISalesCustomerClaimService salesCustomerClaimService;

	private final SalesBindWriter salesBindWriter;

	private final IDistributionUserService distributionUserService;

	/**
	 * 绑定分流入口（对应小程序 POST /app/distribution/share/bind）。
	 * @param dto 注册/绑定请求（userId 已由 Controller 置为当前登录会员）
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void bindFromShare(DistributionUserRegisterDTO dto) {
		if (dto == null || dto.getUserId() == null || dto.getUserId().isBlank()) {
			log.warn("分享绑定请求缺少当前用户，忽略");
			return;
		}
		String customerUserId = dto.getUserId();
		SalesStaff sales = salesStaffService.getActiveByMemberUserId(dto.getInviterUserId());
		if (sales == null) {
			// 邀请人不是（在职）销售：客户已正式归属销售时不再建立新的分销上级关系（需求 §4.2）
			SalesCustomerBind existingBind = salesCustomerBindService.getByCustomerUserId(customerUserId);
			if (existingBind != null) {
				log.info("邀请人不是销售且客户已归属销售，不再建立分销上级关系 customerUserId={}, salesStaffId={}, inviterUserId={}",
						customerUserId, existingBind.getSalesStaffId(), dto.getInviterUserId());
				return;
			}
			// 保持原分销绑定行为
			distributionUserService.register(dto);
			return;
		}
		if (customerUserId.equals(sales.getMemberUserId())) {
			log.info("销售本人点击自己的分享链接，忽略 customerUserId={}", customerUserId);
			return;
		}
		SalesCustomerBind bind = salesCustomerBindService.getByCustomerUserId(customerUserId);
		if (bind != null) {
			if (sales.getId().equals(bind.getSalesStaffId())) {
				log.info("客户已归属该销售，分享绑定幂等返回 customerUserId={}, salesStaffId={}", customerUserId,
						sales.getId());
				return;
			}
			// 已归属其他销售：不改归属，也不新建分销关系
			log.info("客户已归属其他销售，忽略本次分享绑定 customerUserId={}, currentStaffId={}, clickStaffId={}",
					customerUserId, bind.getSalesStaffId(), sales.getId());
			return;
		}
		String bindSource = normalizeSource(dto.getBindSource());
		DistributionUser buyer = distributionUserService.getByUserId(customerUserId);
		String buyerInviter = buyer == null ? null : buyer.getInviterUserId();
		if (buyerInviter == null || buyerInviter.isBlank()) {
			// 客户没有分销上级：直接写入销售归属
			salesBindWriter.upsertBind(customerUserId, sales, bindSource, null, null, "分享链接绑定销售");
			log.info("客户直接绑定销售 customerUserId={}, salesStaffId={}, source={}", customerUserId,
					sales.getId(), bindSource);
			return;
		}
		// 客户已是分销员下级：写待首单认领，正式归属待首单支付后转交
		salesCustomerClaimService.upsertClaim(customerUserId, sales.getId(), bindSource);
		log.info("客户已归属分销员，写待转交认领 customerUserId={}, salesStaffId={}, source={}", customerUserId,
				sales.getId(), bindSource);
	}

	private String normalizeSource(String bindSource) {
		if (SalesCustomerBindSourceEnum.QRCODE.getCode().equalsIgnoreCase(bindSource)) {
			return SalesCustomerBindSourceEnum.QRCODE.getCode();
		}
		return SalesCustomerBindSourceEnum.SHARE.getCode();
	}

}
