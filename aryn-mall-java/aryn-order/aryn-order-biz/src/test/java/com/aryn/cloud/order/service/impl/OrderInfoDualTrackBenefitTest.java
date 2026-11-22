package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.user.api.remote.RemoteEnterpriseUserService;
import com.aryn.cloud.user.api.vo.EnterpriseBenefitsVO;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 双轨会员分轨消费回归（2026-11-15 审查修复）：
 * scene=2/3 企业场景绝不回退个人会员权益（防套利），未定级/无主体/查询失败按无权益处理。
 */
class OrderInfoDualTrackBenefitTest {

	private OrderInfoServiceImpl service;

	private RemoteEnterpriseUserService remoteEnterpriseUserService;

	@BeforeEach
	void setUp() {
		// 只验证私有分轨解析逻辑：CALLS_REAL_METHODS 绕过重型构造器，远程依赖显式注入
		service = mock(OrderInfoServiceImpl.class, org.mockito.Mockito.withSettings()
			.defaultAnswer(org.mockito.Mockito.CALLS_REAL_METHODS));
		remoteEnterpriseUserService = mock(RemoteEnterpriseUserService.class);
		ReflectionTestUtils.setField(service, "remoteEnterpriseUserService", remoteEnterpriseUserService);
	}

	private OrderInfo order(String scene, String vesselId, String buyerOrgId) {
		OrderInfo order = new OrderInfo();
		order.setPurchaseScene(scene);
		order.setVesselId(vesselId);
		order.setBuyerOrgId(buyerOrgId);
		return order;
	}

	private EnterpriseBenefitsVO resolve(OrderInfo order) {
		return ReflectionTestUtils.invokeMethod(service, "resolveEnterpriseBenefits", order);
	}

	private boolean freeShipping(MemberBenefitsVO member, EnterpriseBenefitsVO enterprise) {
		Boolean result = ReflectionTestUtils.invokeMethod(service, "freeShippingFor", member, enterprise);
		return Boolean.TRUE.equals(result);
	}

	private MemberBenefitsVO personalWithFreeShipping() {
		MemberBenefitsVO member = new MemberBenefitsVO();
		member.setFreeShipping(true);
		member.setDiscountRate(new BigDecimal("0.8"));
		return member;
	}

	@Test
	@DisplayName("scene=3 未定级（远程返回 null）：按空企业权益处理，不回退个人折扣/免邮")
	void enterpriseSceneWithoutLevelDoesNotFallbackToPersonal() {
		when(remoteEnterpriseUserService.getSubjectBenefits(any(), any(), any())).thenReturn(null);

		EnterpriseBenefitsVO benefits = resolve(order("3", null, "org-1"));

		assertNotNull(benefits);
		assertNull(benefits.getDiscountRate());
		assertFalse(freeShipping(personalWithFreeShipping(), benefits));
	}

	@Test
	@DisplayName("scene=2 权益查询异常：fail-closed 空权益，不回退个人免邮")
	void enterpriseSceneQueryFailureDoesNotFallbackToPersonal() {
		when(remoteEnterpriseUserService.getSubjectBenefits(any(), any(), any()))
			.thenThrow(new RuntimeException("dubbo timeout"));

		EnterpriseBenefitsVO benefits = resolve(order("2", "vessel-1", null));

		assertNotNull(benefits);
		assertFalse(freeShipping(personalWithFreeShipping(), benefits));
	}

	@Test
	@DisplayName("scene=3 缺主体：空权益兜底，不回退个人轨")
	void enterpriseSceneWithoutSubjectDoesNotFallbackToPersonal() {
		EnterpriseBenefitsVO benefits = resolve(order("3", null, null));

		assertNotNull(benefits);
		assertFalse(freeShipping(personalWithFreeShipping(), benefits));
	}

	@Test
	@DisplayName("scene=1 个人场景：返回 null 走个人轨，个人免邮生效")
	void personalSceneUsesPersonalTrack() {
		EnterpriseBenefitsVO benefits = resolve(order("1", null, null));

		assertNull(benefits);
		assertEquals(true, freeShipping(personalWithFreeShipping(), null));
	}

	@Test
	@DisplayName("企业权益命中：免邮以企业权益为准（个人免邮不交叉）")
	void enterpriseFreeShippingTakesPrecedence() {
		EnterpriseBenefitsVO enterprise = new EnterpriseBenefitsVO();
		enterprise.setFreeShipping(Boolean.TRUE);
		assertEquals(true, freeShipping(null, enterprise));

		enterprise.setFreeShipping(Boolean.FALSE);
		assertFalse(freeShipping(personalWithFreeShipping(), enterprise));
	}

}