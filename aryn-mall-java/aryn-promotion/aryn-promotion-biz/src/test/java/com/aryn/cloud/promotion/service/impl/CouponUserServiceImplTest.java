package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import com.aryn.cloud.promotion.mapper.CouponGoodsMapper;
import com.aryn.cloud.promotion.mapper.CouponInfoMapper;
import com.aryn.cloud.promotion.mapper.CouponUserMapper;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponUserServiceImplTest {

	@Mock private RemoteMallUserService remoteMallUserService;
	@Mock private CouponInfoMapper couponInfoMapper;
	@Mock private CouponGoodsMapper couponGoodsMapper;
	@Mock private CouponUserMapper couponUserMapper;

	private CouponUserServiceImpl service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CouponUser.class);
		service = new TestCouponUserService(remoteMallUserService, couponInfoMapper, couponGoodsMapper,
				couponUserMapper);
		ArynTenantContextHolder.setTenantId("tenant-1");
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void memberBenefitCouponUsesLevelBenefitSourceAndAllocatesStockOnce() {
		when(couponUserMapper.selectCount(any())).thenReturn(0L);
		when(couponInfoMapper.selectCouponById("template-1")).thenReturn(template());
		when(couponUserMapper.insertSourceIfAbsent(any())).thenReturn(1);
		when(couponInfoMapper.allocateOne("template-1")).thenReturn(1);

		assertThat(service.grantMemberBenefitCoupon(
				"template-1", "user-1", "level-1:benefit-1")).isTrue();

		ArgumentCaptor<CouponUser> captor = ArgumentCaptor.forClass(CouponUser.class);
		verify(couponUserMapper).insertSourceIfAbsent(captor.capture());
		assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-1");
		assertThat(captor.getValue().getSourceType()).isEqualTo("MEMBER_BENEFIT");
		assertThat(captor.getValue().getSourceId()).isEqualTo("level-1:benefit-1");
		verify(couponInfoMapper).allocateOne("template-1");
	}

	@Test
	void concurrentDuplicateSourceDoesNotAllocateStockAgain() {
		when(couponUserMapper.selectCount(any())).thenReturn(0L);
		when(couponInfoMapper.selectCouponById("template-1")).thenReturn(template());
		when(couponUserMapper.insertSourceIfAbsent(any())).thenReturn(0);

		assertThat(service.grantMemberBenefitCoupon(
				"template-1", "user-1", "level-1:benefit-1")).isTrue();

		verify(couponInfoMapper, never()).allocateOne(any());
	}

	@Test
	void reserveCouponRequiresUserAvailableStatusAndOrderBinding() {
		when(couponUserMapper.update(any(), any(Wrapper.class))).thenReturn(1);

		assertThat(service.reserveCoupon("coupon-1", "user-1", "order-1")).isTrue();

		ArgumentCaptor<Wrapper<CouponUser>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(couponUserMapper).update(any(), captor.capture());
		AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) captor.getValue();
		assertThat(wrapper.getSqlSegment()).contains("id", "user_id", "status", "validat_time");
		assertThat(wrapper.getParamNameValuePairs().values())
			.contains("coupon-1", "user-1", "order-1", "0", "3");
	}

	@Test
	void releaseCouponRequiresTheOrderThatReservedIt() {
		when(couponUserMapper.update(any(), any(Wrapper.class))).thenReturn(1);

		assertThat(service.releaseCoupon("coupon-1", "order-1")).isTrue();

		ArgumentCaptor<Wrapper<CouponUser>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(couponUserMapper).update(any(), captor.capture());
		AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) captor.getValue();
		assertThat(wrapper.getSqlSegment()).contains("id", "order_id", "status");
		assertThat(wrapper.getParamNameValuePairs().values()).contains("coupon-1", "order-1", "3");
		assertThat(wrapper.getSqlSet()).contains("order_id = NULL");
	}

	private CouponInfo template() {
		return new CouponInfo().setId("template-1").setReceiveEndedAt(LocalDateTime.now().plusDays(1));
	}

	private static final class TestCouponUserService extends CouponUserServiceImpl {

		private TestCouponUserService(RemoteMallUserService remoteMallUserService,
				CouponInfoMapper couponInfoMapper, CouponGoodsMapper couponGoodsMapper,
				CouponUserMapper couponUserMapper) {
			super(remoteMallUserService, couponInfoMapper, couponGoodsMapper);
			this.baseMapper = couponUserMapper;
		}
	}
}
