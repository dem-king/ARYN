package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.DistributionWithdrawApplyDTO;
import com.aryn.cloud.promotion.api.dto.DistributionWithdrawAuditDTO;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.entity.DistributionWithdraw;
import com.aryn.cloud.promotion.api.enums.DistributionWithdrawStatusEnum;
import com.aryn.cloud.promotion.api.vo.DistributionCenterVO;
import com.aryn.cloud.promotion.api.vo.DistributionWithdrawVO;
import com.aryn.cloud.promotion.mapper.DistributionWithdrawMapper;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import com.aryn.cloud.promotion.service.WithdrawAccountCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提现下线后的契约：申请/审核返回明确业务错误，历史查询与账号重加密保留。
 */
class DistributionWithdrawServiceImplTest {

	private IDistributionUserService userService;
	private DistributionWithdrawMapper withdrawMapper;
	private WithdrawAccountCipher cipher;
	private DistributionWithdrawServiceImpl service;

	@BeforeEach
	void setUp() {
		userService = mock(IDistributionUserService.class);
		withdrawMapper = mock(DistributionWithdrawMapper.class);
		String key = Base64.getEncoder().encodeToString(
			"0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
		cipher = new WithdrawAccountCipher(key);
		service = spy(new DistributionWithdrawServiceImpl(userService, cipher));
		ReflectionTestUtils.setField(service, "baseMapper", withdrawMapper);
	}

	@Test
	void applyReturnsOfflineBusinessError() {
		DistributionWithdrawApplyDTO dto = new DistributionWithdrawApplyDTO();
		dto.setAmount(new BigDecimal("20.00"));
		dto.setAccountType("BANK");
		dto.setAccountName("测试用户");
		dto.setAccountNo("6222021234567890");

		assertThatThrownBy(() -> service.apply("user-1", dto))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(throwable -> ((ArynBusinessException) throwable).getMsg())
			.asString()
			.contains("提现功能已下线", "兑换优惠券");

		// 不再产生冻结佣金/提现单
		verify(userService, never()).updateById(any());
		verify(userService, never()).getByUserId(any());
	}

	@Test
	void auditReturnsOfflineBusinessError() {
		DistributionWithdrawAuditDTO dto = new DistributionWithdrawAuditDTO();
		dto.setId("withdraw-1");
		dto.setStatus(DistributionWithdrawStatusEnum.STATUS_1.getCode());

		assertThatThrownBy(() -> service.audit(dto))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(throwable -> ((ArynBusinessException) throwable).getMsg())
			.asString()
			.contains("提现功能已下线", "历史查询");

		verify(userService, never()).updateById(any());
	}

	@Test
	void reencryptLegacyAccountsProcessesPlaintextInBatchesAndIsIdempotent() {
		DistributionWithdraw legacy = pendingWithdraw("withdraw-1", "user-1", "20.00");
		legacy.setAccountNo("6222021234567890");
		when(withdrawMapper.selectLegacyAccounts(200))
			.thenReturn(java.util.List.of(legacy), java.util.List.of());
		doReturn(true).when(service).updateById(legacy);

		int count = service.reencryptLegacyAccounts();

		assertThat(count).isEqualTo(1);
		assertThat(legacy.getAccountNo()).startsWith("v1:");
		assertThat(cipher.decrypt(legacy.getAccountNo())).isEqualTo("6222021234567890");
		verify(withdrawMapper, org.mockito.Mockito.times(2)).selectLegacyAccounts(200);
	}

	@Test
	void adminAndUserPagesReturnMaskedAccountOnly() {
		String encrypted = cipher.encrypt("6222021234567890");
		DistributionWithdraw withdraw = pendingWithdraw("withdraw-1", "user-1", "20.00");
		withdraw.setAccountNo(encrypted);
		Page<DistributionWithdraw> adminEntityPage = new Page<>(1, 10);
		adminEntityPage.setRecords(java.util.List.of(withdraw));
		adminEntityPage.setTotal(1);
		Page<DistributionWithdraw> userEntityPage = new Page<>(1, 10);
		userEntityPage.setRecords(java.util.List.of(withdraw));
		userEntityPage.setTotal(1);
		when(withdrawMapper.selectAdminPage(any(), any())).thenReturn(adminEntityPage);
		when(withdrawMapper.selectPage(any(), any())).thenReturn(userEntityPage);

		IPage<DistributionWithdrawVO> adminPage = service.getAdminPage(new Page<>(1, 10), new DistributionWithdraw());
		IPage<DistributionWithdrawVO> userPage = service.getUserPage(new Page<>(1, 10), "user-1");

		assertThat(adminPage.getRecords().get(0).getAccountNo()).isEqualTo("****7890");
		assertThat(userPage.getRecords().get(0).getAccountNo()).isEqualTo("****7890");
		assertThat(adminPage.getRecords().get(0).getAccountNo()).doesNotContain(encrypted);
	}

	@Test
	void centerIncludesPendingDebtAndInviteCount() {
		DistributionUser user = enabledUser("user-1", "10.00", "2.00");
		user.setPendingCommission(new BigDecimal("3.00"));
		user.setCommissionDebt(new BigDecimal("1.50"));
		user.setSubordinateCount(4);
		when(userService.getByUserId("user-1")).thenReturn(user);
		when(withdrawMapper.selectCount(any())).thenReturn(1L);

		DistributionCenterVO center = service.getCenter("user-1");

		assertThat(center.getPendingCommission()).isEqualByComparingTo("3.00");
		assertThat(center.getCommissionDebt()).isEqualByComparingTo("1.50");
		assertThat(center.getInviteUserCount()).isEqualTo(4);
	}

	private DistributionUser enabledUser(String userId, String available, String frozen) {
		return new DistributionUser()
			.setUserId(userId)
			.setStatus("0")
			.setAvailableCommission(new BigDecimal(available))
			.setFrozenCommission(new BigDecimal(frozen))
			.setWithdrawnCommission(BigDecimal.ZERO);
	}

	private DistributionWithdraw pendingWithdraw(String id, String userId, String amount) {
		return new DistributionWithdraw()
			.setId(id)
			.setWithdrawNo("WD-1")
			.setUserId(userId)
			.setAmount(new BigDecimal(amount))
			.setStatus(DistributionWithdrawStatusEnum.STATUS_0.getCode())
			.setAccountNo(cipher.encrypt("6222021234567890"))
			.setCreateTime(LocalDateTime.now());
	}

}
