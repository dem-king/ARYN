package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.promotion.api.dto.DistributionUserRegisterDTO;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBind;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import com.aryn.cloud.promotion.service.ISalesCustomerBindService;
import com.aryn.cloud.promotion.service.ISalesCustomerClaimService;
import com.aryn.cloud.promotion.service.ISalesStaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分享/扫码绑定分流 7 分支单测（IMPL 4.1）
 */
class SalesCustomerBindFacadeTest {

	private ISalesStaffService salesStaffService;

	private ISalesCustomerBindService salesCustomerBindService;

	private ISalesCustomerClaimService salesCustomerClaimService;

	private SalesBindWriter salesBindWriter;

	private IDistributionUserService distributionUserService;

	private SalesCustomerBindFacade facade;

	@BeforeEach
	void setUp() {
		salesStaffService = mock(ISalesStaffService.class);
		salesCustomerBindService = mock(ISalesCustomerBindService.class);
		salesCustomerClaimService = mock(ISalesCustomerClaimService.class);
		salesBindWriter = mock(SalesBindWriter.class);
		distributionUserService = mock(IDistributionUserService.class);
		facade = new SalesCustomerBindFacade(salesStaffService, salesCustomerBindService,
				salesCustomerClaimService, salesBindWriter, distributionUserService);
	}

	@Test
	void inviterNotSalesKeepsOriginalDistributionRegister() {
		when(salesStaffService.getActiveByMemberUserId("inviter-not-sales")).thenReturn(null);
		when(salesCustomerBindService.getByCustomerUserId("buyer-1")).thenReturn(null);
		DistributionUserRegisterDTO dto = registerDto("buyer-1", "inviter-not-sales", "SHARE");

		facade.bindFromShare(dto);

		verify(distributionUserService).register(dto);
		verify(salesBindWriter, never()).upsertBind(any(), any(), any(), any(), any(), any());
		verify(salesCustomerClaimService, never()).upsertClaim(any(), any(), any());
	}

	@Test
	void inviterNotSalesAndCustomerAlreadyBoundToSalesSkipsRegister() {
		when(salesStaffService.getActiveByMemberUserId("inviter-not-sales")).thenReturn(null);
		when(salesCustomerBindService.getByCustomerUserId("buyer-1"))
			.thenReturn(new SalesCustomerBind().setCustomerUserId("buyer-1").setSalesStaffId("staff-1"));
		DistributionUserRegisterDTO dto = registerDto("buyer-1", "inviter-not-sales", "SHARE");

		facade.bindFromShare(dto);

		verify(salesCustomerBindService).getByCustomerUserId("buyer-1");
		// 客户已正式归属销售：不再建立新的分销上级关系（需求 §4.2）
		verify(distributionUserService, never()).register(any());
		verify(salesBindWriter, never()).upsertBind(any(), any(), any(), any(), any(), any());
		verify(salesCustomerClaimService, never()).upsertClaim(any(), any(), any());
	}

	@Test
	void salesPersonClickingOwnShareLinkIsIgnored() {
		when(salesStaffService.getActiveByMemberUserId("member-1")).thenReturn(activeStaff("staff-1", "member-1"));
		DistributionUserRegisterDTO dto = registerDto("member-1", "member-1", "SHARE");

		facade.bindFromShare(dto);

		verify(distributionUserService, never()).register(any());
		verify(salesBindWriter, never()).upsertBind(any(), any(), any(), any(), any(), any());
		verify(salesCustomerClaimService, never()).upsertClaim(any(), any(), any());
	}

	@Test
	void sameSalesBindIsIdempotent() {
		when(salesStaffService.getActiveByMemberUserId("member-1")).thenReturn(activeStaff("staff-1", "member-1"));
		when(salesCustomerBindService.getByCustomerUserId("buyer-1"))
			.thenReturn(new SalesCustomerBind().setCustomerUserId("buyer-1").setSalesStaffId("staff-1"));
		DistributionUserRegisterDTO dto = registerDto("buyer-1", "member-1", "SHARE");

		facade.bindFromShare(dto);

		verify(distributionUserService, never()).register(any());
		verify(salesBindWriter, never()).upsertBind(any(), any(), any(), any(), any(), any());
	}

	@Test
	void customerBoundToAnotherSalesIsNotRebound() {
		when(salesStaffService.getActiveByMemberUserId("member-1")).thenReturn(activeStaff("staff-1", "member-1"));
		when(salesCustomerBindService.getByCustomerUserId("buyer-1"))
			.thenReturn(new SalesCustomerBind().setCustomerUserId("buyer-1").setSalesStaffId("staff-2"));
		DistributionUserRegisterDTO dto = registerDto("buyer-1", "member-1", "SHARE");

		facade.bindFromShare(dto);

		verify(salesBindWriter, never()).upsertBind(any(), any(), any(), any(), any(), any());
		verify(salesCustomerClaimService, never()).upsertClaim(any(), any(), any());
		// 已归属销售后也不再建立分销关系
		verify(distributionUserService, never()).register(any());
	}

	@Test
	void customerWithoutSalesBindAndWithoutDistributorInviterIsBoundDirectly() {
		SalesStaff staff = activeStaff("staff-1", "member-1");
		when(salesStaffService.getActiveByMemberUserId("member-1")).thenReturn(staff);
		when(salesCustomerBindService.getByCustomerUserId("buyer-1")).thenReturn(null);
		when(distributionUserService.getByUserId("buyer-1")).thenReturn(null);
		DistributionUserRegisterDTO dto = registerDto("buyer-1", "member-1", "QRCODE");

		facade.bindFromShare(dto);

		verify(salesBindWriter).upsertBind(eq("buyer-1"), eq(staff), eq("QRCODE"), eq(null), eq(null),
				anyString());
		verify(salesCustomerClaimService, never()).upsertClaim(any(), any(), any());
	}

	@Test
	void customerWithDistributorInviterGetsPendingClaimInsteadOfRebind() {
		SalesStaff staff = activeStaff("staff-1", "member-1");
		when(salesStaffService.getActiveByMemberUserId("member-1")).thenReturn(staff);
		when(salesCustomerBindService.getByCustomerUserId("buyer-1")).thenReturn(null);
		when(distributionUserService.getByUserId("buyer-1"))
			.thenReturn(new DistributionUser().setUserId("buyer-1").setInviterUserId("distributor-1"));
		DistributionUserRegisterDTO dto = registerDto("buyer-1", "member-1", "SHARE");

		facade.bindFromShare(dto);

		verify(salesCustomerClaimService).upsertClaim("buyer-1", "staff-1", "SHARE");
		verify(salesBindWriter, never()).upsertBind(any(), any(), any(), any(), any(), any());
		// 不能把销售写成客户的分销上级
		verify(distributionUserService, never()).register(any());
	}

	private DistributionUserRegisterDTO registerDto(String userId, String inviter, String bindSource) {
		DistributionUserRegisterDTO dto = new DistributionUserRegisterDTO();
		dto.setUserId(userId);
		dto.setInviterUserId(inviter);
		dto.setBindSource(bindSource);
		return dto;
	}

	private SalesStaff activeStaff(String id, String memberUserId) {
		return new SalesStaff().setId(id).setMemberUserId(memberUserId).setStaffName("李四").setStatus("0");
	}

}
