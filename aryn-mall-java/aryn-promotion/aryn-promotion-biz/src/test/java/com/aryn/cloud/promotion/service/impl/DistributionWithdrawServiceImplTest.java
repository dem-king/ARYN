package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.DistributionWithdrawApplyDTO;
import com.aryn.cloud.promotion.api.dto.DistributionWithdrawAuditDTO;
import com.aryn.cloud.promotion.api.entity.DistributionConfig;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.entity.DistributionWithdraw;
import com.aryn.cloud.promotion.api.enums.DistributionWithdrawStatusEnum;
import com.aryn.cloud.promotion.api.vo.DistributionWithdrawVO;
import com.aryn.cloud.promotion.api.vo.DistributionCenterVO;
import com.aryn.cloud.promotion.mapper.DistributionWithdrawMapper;
import com.aryn.cloud.promotion.service.IDistributionCommissionFlowService;
import com.aryn.cloud.promotion.service.IDistributionConfigService;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import com.aryn.cloud.promotion.service.WithdrawAccountCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributionWithdrawServiceImplTest {

	private IDistributionUserService userService;
	private IDistributionConfigService configService;
	private IDistributionCommissionFlowService flowService;
	private DistributionWithdrawMapper withdrawMapper;
	private WithdrawAccountCipher cipher;
	private DistributionWithdrawServiceImpl service;

	@BeforeEach
	void setUp() {
		userService = mock(IDistributionUserService.class);
		configService = mock(IDistributionConfigService.class);
		flowService = mock(IDistributionCommissionFlowService.class);
		withdrawMapper = mock(DistributionWithdrawMapper.class);
		String key = Base64.getEncoder().encodeToString(
			"0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
		cipher = new WithdrawAccountCipher(key);
		service = spy(new DistributionWithdrawServiceImpl(userService, configService, flowService, cipher));
		ReflectionTestUtils.setField(service, "baseMapper", withdrawMapper);
	}

	@Test
	void applyStoresEncryptedAccountOnly() {
		DistributionUser user = enabledUser("user-1", "100.00", "0.00");
		when(userService.getByUserId("user-1")).thenReturn(user);
		when(configService.getActiveConfig()).thenReturn(new DistributionConfig().setMinWithdrawAmount(BigDecimal.ONE));
		when(userService.updateById(any())).thenReturn(true);
		doReturn(true).when(service).save(any(DistributionWithdraw.class));
		DistributionWithdrawApplyDTO dto = applyDto("20.00", "6222021234567890");

		service.apply("user-1", dto);

		ArgumentCaptor<DistributionWithdraw> captor = ArgumentCaptor.forClass(DistributionWithdraw.class);
		verify(service).save(captor.capture());
		String stored = captor.getValue().getAccountNo();
		assertThat(stored).startsWith("v1:").doesNotContain("6222021234567890");
		assertThat(cipher.decrypt(stored)).isEqualTo("6222021234567890");
	}

	@Test
	void approvingWithdrawRequiresPayoutNo() {
		DistributionWithdraw withdraw = pendingWithdraw("withdraw-1", "user-1", "20.00");
		doReturn(withdraw).when(service).getById("withdraw-1");
		when(userService.getByUserId("user-1")).thenReturn(enabledUser("user-1", "0.00", "20.00"));
		DistributionWithdrawAuditDTO dto = auditDto("withdraw-1", DistributionWithdrawStatusEnum.STATUS_1.getCode(), null);

		assertThatThrownBy(() -> service.audit(dto))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(throwable -> ((ArynBusinessException) throwable).getMsg())
			.asString()
			.contains("打款流水号");
	}

	@Test
	void approvingWithdrawRecordsPayoutAndDeductsFrozenBalance() {
		DistributionWithdraw withdraw = pendingWithdraw("withdraw-1", "user-1", "20.00");
		DistributionUser user = enabledUser("user-1", "5.00", "20.00");
		doReturn(withdraw).when(service).getById("withdraw-1");
		doReturn(true).when(service).updateById(any(DistributionWithdraw.class));
		when(userService.getByUserId("user-1")).thenReturn(user);
		when(userService.updateById(any())).thenReturn(true);
		when(flowService.save(any())).thenReturn(true);
		DistributionWithdrawAuditDTO dto = auditDto(
			"withdraw-1", DistributionWithdrawStatusEnum.STATUS_1.getCode(), "BANK-20260717-001");

		service.audit(dto);

		assertThat(user.getFrozenCommission()).isEqualByComparingTo("0.00");
		assertThat(user.getWithdrawnCommission()).isEqualByComparingTo("20.00");
		assertThat(withdraw.getPayoutNo()).isEqualTo("BANK-20260717-001");
		assertThat(withdraw.getPayoutTime()).isNotNull();
		assertThat(withdraw.getPayoutBy()).isNotBlank();
	}

	@Test
	void approvingWithdrawRollsBackWhenCommissionFlowCannotBeSaved() {
		DistributionWithdraw withdraw = pendingWithdraw("withdraw-1", "user-1", "20.00");
		DistributionUser user = enabledUser("user-1", "5.00", "20.00");
		doReturn(withdraw).when(service).getById("withdraw-1");
		when(userService.getByUserId("user-1")).thenReturn(user);
		when(userService.updateById(any())).thenReturn(true);
		when(flowService.save(any())).thenReturn(false);

		assertThatThrownBy(() -> service.audit(auditDto(
			"withdraw-1", DistributionWithdrawStatusEnum.STATUS_1.getCode(), "BANK-20260717-002")))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(throwable -> ((ArynBusinessException) throwable).getMsg())
			.asString()
			.contains("流水");
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
	void rejectingWithdrawReturnsFrozenBalanceToAvailableBalance() {
		DistributionWithdraw withdraw = pendingWithdraw("withdraw-1", "user-1", "20.00");
		DistributionUser user = enabledUser("user-1", "5.00", "20.00");
		doReturn(withdraw).when(service).getById("withdraw-1");
		doReturn(true).when(service).updateById(any(DistributionWithdraw.class));
		when(userService.getByUserId("user-1")).thenReturn(user);
		when(userService.updateById(any())).thenReturn(true);

		service.audit(auditDto("withdraw-1", DistributionWithdrawStatusEnum.STATUS_2.getCode(), null));

		assertThat(user.getFrozenCommission()).isEqualByComparingTo("0.00");
		assertThat(user.getAvailableCommission()).isEqualByComparingTo("25.00");
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

	private DistributionWithdrawApplyDTO applyDto(String amount, String accountNo) {
		DistributionWithdrawApplyDTO dto = new DistributionWithdrawApplyDTO();
		dto.setAmount(new BigDecimal(amount));
		dto.setAccountType("BANK");
		dto.setAccountName("测试用户");
		dto.setAccountNo(accountNo);
		return dto;
	}

	private DistributionWithdrawAuditDTO auditDto(String id, String status, String payoutNo) {
		DistributionWithdrawAuditDTO dto = new DistributionWithdrawAuditDTO();
		dto.setId(id);
		dto.setStatus(status);
		dto.setPayoutNo(payoutNo);
		return dto;
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
