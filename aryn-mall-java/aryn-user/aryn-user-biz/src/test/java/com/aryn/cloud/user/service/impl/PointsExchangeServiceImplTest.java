package com.aryn.cloud.user.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.remote.RemoteCouponInfoService;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.vo.CouponTemplateInfoVO;
import com.aryn.cloud.user.api.entity.PointsExchange;
import com.aryn.cloud.user.api.entity.PointsExchangeRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.enums.PointsSceneEnum;
import com.aryn.cloud.user.api.vo.PointsExchangeVO;
import com.aryn.cloud.user.mapper.PointsExchangeMapper;
import com.aryn.cloud.user.mapper.PointsExchangeRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IPointsGrantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 积分兑券：顺序「落记录 → 扣积分 → 发券」，任一步失败整体回滚（事务语义）；
 * 限兑/下架/配置非法在扣积分前拦截。
 */
@ExtendWith(MockitoExtension.class)
class PointsExchangeServiceImplTest {

	@Mock
	private PointsExchangeRecordMapper exchangeRecordMapper;

	@Mock
	private UserInfoMapper userInfoMapper;

	@Mock
	private IPointsGrantService pointsGrantService;

	@Mock
	private RemoteCouponInfoService remoteCouponInfoService;

	@Mock
	private RemoteCouponUserService remoteCouponUserService;

	@Mock
	private PointsExchangeMapper exchangeMapper;

	private PointsExchangeServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new TestPointsExchangeService(exchangeRecordMapper, userInfoMapper, pointsGrantService,
				remoteCouponInfoService, remoteCouponUserService, exchangeMapper);
	}

	@Test
	void exchangeRecordsDeductsPointsThenGrantsCoupon() {
		PointsExchange exchange = exchange("ex-1", 500, 1, "0");
		when(exchangeMapper.selectById("ex-1")).thenReturn(exchange);
		when(exchangeRecordMapper.selectCount(any())).thenReturn(0L);
		when(exchangeRecordMapper.insert(any(PointsExchangeRecord.class))).thenReturn(1);
		when(remoteCouponUserService.grantPointsExchangeCoupon(eq("coupon-1"), eq("user-1"), anyString()))
			.thenReturn(true);

		PointsExchangeRecord record = service.exchange("user-1", "ex-1");

		assertEquals(500, record.getPointsCost());
		assertEquals("coupon-1", record.getCouponId());
		// 扣积分幂等键挂记录ID，场景为积分兑换
		verify(pointsGrantService).consume(eq("user-1"), eq(500), eq(PointsSceneEnum.POINTS_EXCHANGE),
				eq("POINTS_EXCHANGE:" + record.getId()), anyString());
		verify(remoteCouponUserService).grantPointsExchangeCoupon("coupon-1", "user-1", record.getId());
	}

	@Test
	void exchangeRejectsWhenPerUserLimitReached() {
		when(exchangeMapper.selectById("ex-1")).thenReturn(exchange("ex-1", 500, 1, "0"));
		when(exchangeRecordMapper.selectCount(any())).thenReturn(1L);

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.exchange("user-1", "ex-1"));

		assertEquals("本项每人限兑 1 次，已达上限", exception.getMsg());
		verify(exchangeRecordMapper, never()).insert(any(PointsExchangeRecord.class));
		verify(pointsGrantService, never()).consume(anyString(), any(), any(), anyString(), anyString());
	}

	@Test
	void exchangeRejectsDisabledExchange() {
		when(exchangeMapper.selectById("ex-1")).thenReturn(exchange("ex-1", 500, 0, "1"));

		assertThrows(ArynBusinessException.class, () -> service.exchange("user-1", "ex-1"));
		verify(exchangeRecordMapper, never()).insert(any(PointsExchangeRecord.class));
	}

	/**
	 * 发券失败（返回 false / 抛错）必须向上抛：本地事务回滚扣积分与记录，
	 * 最坏情形是用户什么都没得到、可重试，不出现「券已发积分没扣」。
	 */
	@Test
	void exchangePropagatesGrantFailure() {
		when(exchangeMapper.selectById("ex-1")).thenReturn(exchange("ex-1", 500, 0, "0"));
		when(exchangeRecordMapper.insert(any(PointsExchangeRecord.class))).thenReturn(1);
		when(remoteCouponUserService.grantPointsExchangeCoupon(eq("coupon-1"), eq("user-1"), anyString()))
			.thenThrow(new ArynBusinessException("积分兑换优惠券库存不足"));

		assertThrows(ArynBusinessException.class, () -> service.exchange("user-1", "ex-1"));
		verify(pointsGrantService).consume(anyString(), any(), any(), anyString(), anyString());
	}

	@Test
	void listApplicableMarksUnexchangeableReasons() {
		PointsExchange affordable = exchange("ex-1", 100, 1, "0");
		PointsExchange expensive = exchange("ex-2", 99999, 0, "0").setCouponId("coupon-2");
		PointsExchange soldOut = exchange("ex-3", 100, 0, "0").setCouponId("coupon-3");
		when(exchangeMapper.selectList(any())).thenReturn(List.of(affordable, expensive, soldOut));
		when(userInfoMapper.selectById("user-1")).thenReturn(new UserInfo().setId("user-1").setPoint(500));
		// ex-2 已兑 1 次（不限兑，仅计数展示）；ex-1 未兑
		when(exchangeRecordMapper.selectList(any())).thenReturn(List.of(
				new PointsExchangeRecord().setUserId("user-1").setExchangeId("ex-2")));
		CouponTemplateInfoVO template = new CouponTemplateInfoVO();
		template.setCouponName("5 元无门槛券");
		template.setCouponType("1");
		template.setAmount(new BigDecimal("5"));
		template.setThreshold(BigDecimal.ZERO);
		template.setRemainNum(10);
		CouponTemplateInfoVO soldOutTemplate = new CouponTemplateInfoVO();
		soldOutTemplate.setCouponName("限量券");
		soldOutTemplate.setRemainNum(0);
		lenient().when(remoteCouponInfoService.getTemplate("coupon-1")).thenReturn(template);
		lenient().when(remoteCouponInfoService.getTemplate("coupon-2")).thenReturn(template);
		lenient().when(remoteCouponInfoService.getTemplate("coupon-3")).thenReturn(soldOutTemplate);

		List<PointsExchangeVO> result = service.listApplicable("user-1");

		assertEquals(3, result.size());
		PointsExchangeVO first = result.get(0);
		assertTrue(first.isExchangeable());
		assertEquals("5 元无门槛券", first.getCouponName());
		assertEquals(0, first.getExchangedCount());
		// 余额 500 < 99999：积分不足（模板缺失时券名回落标题，不阻断列表）
		assertEquals("积分不足", result.get(1).getUnexchangeableReason());
		assertFalse(result.get(1).isExchangeable());
		assertEquals(1, result.get(1).getExchangedCount());
		// 券模板余量 0：已被抢完（判 定优先于余额）
		assertEquals("已被抢完", result.get(2).getUnexchangeableReason());
	}

	@Test
	void saveExchangeValidatesPointsCostAndTemplate() {
		PointsExchange bad = exchange("ex-1", 0, 0, "0");

		assertEquals("兑换所需积分必须大于0", assertThrows(ArynBusinessException.class,
				() -> service.saveExchange(bad)).getMsg());

		PointsExchange missingCoupon = exchange("", 100, 0, "0");
		missingCoupon.setCouponId(null);
		assertEquals("请选择优惠券模板", assertThrows(ArynBusinessException.class,
				() -> service.saveExchange(missingCoupon)).getMsg());
	}

	private PointsExchange exchange(String id, int pointsCost, int perUserLimit, String status) {
		return new PointsExchange().setId(id).setTitle("5 元券").setPointsCost(pointsCost)
			.setCouponId("coupon-1")
			.setPerUserLimit(perUserLimit)
			.setStatus(status);
	}

	private static final class TestPointsExchangeService extends PointsExchangeServiceImpl {

		private TestPointsExchangeService(PointsExchangeRecordMapper exchangeRecordMapper,
				UserInfoMapper userInfoMapper, IPointsGrantService pointsGrantService,
				RemoteCouponInfoService remoteCouponInfoService, RemoteCouponUserService remoteCouponUserService,
				PointsExchangeMapper exchangeMapper) {
			super(exchangeRecordMapper, userInfoMapper, pointsGrantService, remoteCouponInfoService,
					remoteCouponUserService);
			this.baseMapper = exchangeMapper;
		}
	}

}
