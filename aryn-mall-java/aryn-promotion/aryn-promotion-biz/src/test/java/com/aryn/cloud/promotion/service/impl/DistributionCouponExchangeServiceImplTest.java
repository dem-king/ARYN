package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.DistributionCouponExchangeDTO;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import com.aryn.cloud.promotion.api.entity.DistributionCommissionFlow;
import com.aryn.cloud.promotion.api.entity.DistributionCouponExchange;
import com.aryn.cloud.promotion.api.entity.DistributionCouponPack;
import com.aryn.cloud.promotion.api.entity.DistributionCouponPackItem;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.mapper.CouponInfoMapper;
import com.aryn.cloud.promotion.mapper.CouponUserMapper;
import com.aryn.cloud.promotion.mapper.DistributionCommissionFlowMapper;
import com.aryn.cloud.promotion.mapper.DistributionCouponExchangeMapper;
import com.aryn.cloud.promotion.mapper.DistributionCouponPackItemMapper;
import com.aryn.cloud.promotion.mapper.DistributionCouponPackMapper;
import com.aryn.cloud.promotion.mapper.DistributionUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 佣金兑换优惠券：成功扣款发券、余额不足、库存不足、发券失败回滚、重复兑换。
 */
@ExtendWith(MockitoExtension.class)
class DistributionCouponExchangeServiceImplTest {

	@Mock
	private DistributionCouponPackMapper packMapper;

	@Mock
	private DistributionCouponPackItemMapper packItemMapper;

	@Mock
	private DistributionCouponExchangeMapper exchangeMapper;

	@Mock
	private DistributionUserMapper userMapper;

	@Mock
	private DistributionCommissionFlowMapper commissionFlowMapper;

	@Mock
	private CouponInfoMapper couponInfoMapper;

	@Mock
	private CouponUserMapper couponUserMapper;

	private DistributionCouponExchangeServiceImpl service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
			DistributionCouponPackItem.class);
		service = new DistributionCouponExchangeServiceImpl(packMapper, packItemMapper, exchangeMapper, userMapper,
			commissionFlowMapper, couponInfoMapper, couponUserMapper);
		ArynTenantContextHolder.setTenantId("tenant-1");
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void exchangeDeductsCommissionWritesFlowAndIssuesCoupons() {
		DistributionCouponPack pack = pack("pack-1", "满100减20券包", "30.00", "100.00", 10, 30);
		DistributionUser user = user("user-1", "100.00", "0", "分销员甲");
		stubPackAndTemplates(pack);
		when(userMapper.selectByUserIdForUpdate("user-1")).thenReturn(user);
		when(packMapper.deductStock("pack-1")).thenReturn(1);
		when(userMapper.updateById(user)).thenReturn(1);
		when(commissionFlowMapper.insert(any(DistributionCommissionFlow.class))).thenReturn(1);
		when(couponUserMapper.insertSourceIfAbsent(any())).thenReturn(1);
		when(couponInfoMapper.allocateOne(anyString())).thenReturn(1);
		when(exchangeMapper.insert(any(DistributionCouponExchange.class))).thenReturn(1);

		assertThat(service.exchange("user-1", dto("pack-1"))).isTrue();

		// 佣金：只扣 available_commission，total_commission 不变
		assertThat(user.getAvailableCommission()).isEqualByComparingTo("70.00");
		assertThat(user.getTotalCommission()).isEqualByComparingTo("500.00");

		ArgumentCaptor<DistributionCommissionFlow> flowCaptor = ArgumentCaptor
			.forClass(DistributionCommissionFlow.class);
		verify(commissionFlowMapper).insert(flowCaptor.capture());
		assertThat(flowCaptor.getValue().getFlowType()).isEqualTo("EXPENSE");
		assertThat(flowCaptor.getValue().getAmount()).isEqualByComparingTo("30.00");
		assertThat(flowCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("70.00");
		assertThat(flowCaptor.getValue().getRemark()).contains("满100减20券包");
		assertThat(flowCaptor.getValue().getBizOrderId()).isNotBlank();

		ArgumentCaptor<CouponUser> couponCaptor = ArgumentCaptor.forClass(CouponUser.class);
		verify(couponUserMapper, times(3)).insertSourceIfAbsent(couponCaptor.capture());
		List<CouponUser> issued = couponCaptor.getAllValues();
		assertThat(issued).allSatisfy(couponUser -> {
			assertThat(couponUser.getUserId()).isEqualTo("user-1");
			assertThat(couponUser.getStatus()).isEqualTo("0");
			assertThat(couponUser.getSourceType()).isEqualTo("COUPON_EXCHANGE");
			assertThat(couponUser.getTenantId()).isEqualTo("tenant-1");
			assertThat(couponUser.getValidatTime()).isAfter(LocalDateTime.now().plusDays(29));
		});
		assertThat(issued.stream().map(CouponUser::getSourceId).distinct().count()).isEqualTo(3);
		assertThat(issued.stream().filter(c -> "ci-1".equals(c.getCouponId())).count()).isEqualTo(1);
		assertThat(issued.stream().filter(c -> "ci-2".equals(c.getCouponId())).count()).isEqualTo(2);
		verify(couponInfoMapper, times(1)).allocateOne("ci-1");
		verify(couponInfoMapper, times(2)).allocateOne("ci-2");
		verify(packMapper).deductStock("pack-1");

		ArgumentCaptor<DistributionCouponExchange> exchangeCaptor = ArgumentCaptor
			.forClass(DistributionCouponExchange.class);
		verify(exchangeMapper).insert(exchangeCaptor.capture());
		DistributionCouponExchange record = exchangeCaptor.getValue();
		assertThat(record.getUserId()).isEqualTo("user-1");
		assertThat(record.getPackId()).isEqualTo("pack-1");
		assertThat(record.getStatus()).isEqualTo("1");
		assertThat(record.getCommissionAmount()).isEqualByComparingTo("30.00");
		assertThat(record.getFaceAmount()).isEqualByComparingTo("100.00");
		assertThat(record.getCouponUserIds().split(",")).hasSize(3);
		assertThat(record.getTenantId()).isEqualTo("tenant-1");
	}

	@Test
	void exchangeRejectsWhenAvailableCommissionInsufficient() {
		DistributionCouponPack pack = pack("pack-1", "满100减20券包", "30.00", "100.00", 10, 30);
		stubPackAndTemplates(pack);
		when(userMapper.selectByUserIdForUpdate("user-1")).thenReturn(user("user-1", "10.00", "0", "分销员甲"));

		assertThatThrownBy(() -> service.exchange("user-1", dto("pack-1")))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(throwable -> ((ArynBusinessException) throwable).getMsg())
			.asString()
			.contains("佣金不足");

		verify(packMapper, never()).deductStock(any());
		verify(userMapper, never()).updateById(any(DistributionUser.class));
		verifyNoInteractions(commissionFlowMapper, exchangeMapper, couponUserMapper);
	}

	@Test
	void exchangeRejectsWhenPackStockSoldOut() {
		DistributionCouponPack pack = pack("pack-1", "满100减20券包", "30.00", "100.00", 0, 30);
		stubPackAndTemplates(pack);
		when(userMapper.selectByUserIdForUpdate("user-1"))
			.thenReturn(user("user-1", "100.00", "0", "分销员甲"));

		assertThatThrownBy(() -> service.exchange("user-1", dto("pack-1")))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(throwable -> ((ArynBusinessException) throwable).getMsg())
			.asString()
			.contains("库存不足");

		verify(packMapper, never()).deductStock(any());
		verify(userMapper, never()).updateById(any(DistributionUser.class));
		verifyNoInteractions(commissionFlowMapper, exchangeMapper, couponUserMapper);
	}

	@Test
	void exchangeRollsBackCommissionWhenCouponIssuanceFails() throws NoSuchMethodException {
		DistributionCouponPack pack = pack("pack-1", "满100减20券包", "30.00", "100.00", 10, 30);
		DistributionUser user = user("user-1", "100.00", "0", "分销员甲");
		stubPackAndTemplates(pack);
		when(userMapper.selectByUserIdForUpdate("user-1")).thenReturn(user);
		when(packMapper.deductStock("pack-1")).thenReturn(1);
		when(userMapper.updateById(user)).thenReturn(1);
		when(commissionFlowMapper.insert(any(DistributionCommissionFlow.class))).thenReturn(1);
		when(couponUserMapper.insertSourceIfAbsent(any())).thenReturn(1);
		// 券模板入库成功但扣减库存失败：模拟发券失败
		when(couponInfoMapper.allocateOne(anyString())).thenReturn(0);

		assertThatThrownBy(() -> service.exchange("user-1", dto("pack-1")))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(throwable -> ((ArynBusinessException) throwable).getMsg())
			.asString()
			.contains("库存不足");

		// 佣金已扣、流水已写，但兑换记录未落库；整个方法在同一事务内，异常会让佣金扣减回滚
		verify(userMapper).updateById(user);
		verify(commissionFlowMapper).insert(any(DistributionCommissionFlow.class));
		verify(exchangeMapper, never()).insert(any(DistributionCouponExchange.class));
		assertExchangeIsTransactional();
	}

	@Test
	void repeatExchangeIssuesCouponsAndDeductsCommissionEachTime() {
		DistributionCouponPack pack = pack("pack-1", "满100减20券包", "30.00", "100.00", 10, 30);
		DistributionUser user = user("user-1", "100.00", "0", "分销员甲");
		stubPackAndTemplates(pack);
		when(userMapper.selectByUserIdForUpdate("user-1")).thenReturn(user);
		when(packMapper.deductStock("pack-1")).thenReturn(1);
		when(userMapper.updateById(user)).thenReturn(1);
		when(commissionFlowMapper.insert(any(DistributionCommissionFlow.class))).thenReturn(1);
		when(couponUserMapper.insertSourceIfAbsent(any())).thenReturn(1);
		when(couponInfoMapper.allocateOne(anyString())).thenReturn(1);
		when(exchangeMapper.insert(any(DistributionCouponExchange.class))).thenReturn(1);

		assertThat(service.exchange("user-1", dto("pack-1"))).isTrue();
		assertThat(service.exchange("user-1", dto("pack-1"))).isTrue();

		// 业务未限制每人兑换次数，允许重复兑换；每笔独立扣佣金、独立发券
		assertThat(user.getAvailableCommission()).isEqualByComparingTo("40.00");
		verify(exchangeMapper, times(2)).insert(any(DistributionCouponExchange.class));
		verify(couponUserMapper, times(6)).insertSourceIfAbsent(any());
		verify(commissionFlowMapper, times(2)).insert(any(DistributionCommissionFlow.class));
	}

	@Test
	void exchangeRejectsDisabledDistributionUser() {
		DistributionCouponPack pack = pack("pack-1", "满100减20券包", "30.00", "100.00", 10, 30);
		stubPackAndTemplates(pack);
		when(userMapper.selectByUserIdForUpdate("user-1"))
			.thenReturn(user("user-1", "100.00", "1", "分销员甲"));

		assertThatThrownBy(() -> service.exchange("user-1", dto("pack-1")))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(throwable -> ((ArynBusinessException) throwable).getMsg())
			.asString()
			.contains("禁用");

		verifyNoInteractions(commissionFlowMapper, exchangeMapper, couponUserMapper);
	}

	private void stubPackAndTemplates(DistributionCouponPack pack) {
		when(packMapper.selectById(pack.getId())).thenReturn(pack);
		when(packItemMapper.selectList(any())).thenReturn(List.of(
			new DistributionCouponPackItem().setId("item-1").setPackId(pack.getId()).setCouponInfoId("ci-1")
				.setQuantity(1).setTenantId("tenant-1"),
			new DistributionCouponPackItem().setId("item-2").setPackId(pack.getId()).setCouponInfoId("ci-2")
				.setQuantity(2).setTenantId("tenant-1")));
		when(couponInfoMapper.selectCouponById("ci-1")).thenReturn(template("ci-1", "满100减10", "10.00"));
		when(couponInfoMapper.selectCouponById("ci-2")).thenReturn(template("ci-2", "满200减20", "20.00"));
	}

	private DistributionCouponPack pack(String id, String packName, String requiredCommission, String faceAmount,
			Integer stock, Integer validDays) {
		return new DistributionCouponPack()
			.setId(id)
			.setPackName(packName)
			.setStatus("0")
			.setFaceAmount(new BigDecimal(faceAmount))
			.setRequiredCommission(new BigDecimal(requiredCommission))
			.setStock(stock)
			.setValidDays(validDays)
			.setTenantId("tenant-1");
	}

	private DistributionUser user(String userId, String available, String status, String nickname) {
		return new DistributionUser()
			.setId("du-" + userId)
			.setUserId(userId)
			.setStatus(status)
			.setNickname(nickname)
			.setAvailableCommission(new BigDecimal(available))
			.setTotalCommission(new BigDecimal("500.00"))
			.setPendingCommission(BigDecimal.ZERO)
			.setCommissionDebt(BigDecimal.ZERO)
			.setSubordinateCount(0)
			.setTenantId("tenant-1");
	}

	private CouponInfo template(String id, String name, String amount) {
		return new CouponInfo()
			.setId(id)
			.setCouponName(name)
			.setAmount(new BigDecimal(amount))
			.setRemainNum(100)
			.setStatus("0")
			.setReceiveStartedAt(LocalDateTime.now().minusDays(1))
			.setReceiveEndedAt(LocalDateTime.now().plusDays(30));
	}

	private DistributionCouponExchangeDTO dto(String packId) {
		DistributionCouponExchangeDTO dto = new DistributionCouponExchangeDTO();
		dto.setPackId(packId);
		return dto;
	}

	private void assertExchangeIsTransactional() throws NoSuchMethodException {
		Transactional transactional = DistributionCouponExchangeServiceImpl.class
			.getMethod("exchange", String.class, DistributionCouponExchangeDTO.class)
			.getAnnotation(Transactional.class);
		assertThat(transactional).isNotNull();
		assertThat(transactional.rollbackFor()).contains(Exception.class);
	}

}
