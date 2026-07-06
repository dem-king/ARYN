
package com.aryn.cloud.promotion.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import com.aryn.cloud.promotion.api.entity.PointsExchangeRecord;
import com.aryn.cloud.promotion.api.entity.PointsGoods;
import com.aryn.cloud.promotion.mapper.CouponInfoMapper;
import com.aryn.cloud.promotion.mapper.CouponUserMapper;
import com.aryn.cloud.promotion.mapper.PointsExchangeRecordMapper;
import com.aryn.cloud.promotion.mapper.PointsGoodsMapper;
import com.aryn.cloud.promotion.service.IPointsGoodsService;
import com.aryn.cloud.user.api.remote.IUserPointsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 积分商品 ServiceImpl
 *
 * @author aryn
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsGoodsServiceImpl extends ServiceImpl<PointsGoodsMapper, PointsGoods>
		implements IPointsGoodsService {

	private final PointsGoodsMapper pointsGoodsMapper;

	private final PointsExchangeRecordMapper pointsExchangeRecordMapper;

	private final CouponInfoMapper couponInfoMapper;

	private final CouponUserMapper couponUserMapper;

	@DubboReference
	private IUserPointsApi userPointsApi;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PointsExchangeRecord exchangePointsGoods(String userId, String goodsId) {
		// 1. 校验商品状态（存在性、活动时间）
		PointsGoods goods = pointsGoodsMapper.selectById(goodsId);
		if (ObjectUtil.isNull(goods)) {
			throw new ArynBusinessException("积分商品不存在");
		}
		LocalDateTime now = LocalDateTime.now();
		if (goods.getStartTime() != null && now.isBefore(goods.getStartTime())) {
			throw new ArynBusinessException("活动尚未开始");
		}
		if (goods.getEndTime() != null && now.isAfter(goods.getEndTime())) {
			throw new ArynBusinessException("活动已结束");
		}

		// 2. 校验库存 > 0
		if (goods.getStock() <= 0) {
			throw new ArynBusinessException("库存不足");
		}

		// 3. 校验限购（查询用户成功兑换记录数 vs limitPerUser）
		if (goods.getLimitPerUser() != null && goods.getLimitPerUser() > 0) {
			long exchangedCount = pointsExchangeRecordMapper.selectCount(
					Wrappers.<PointsExchangeRecord>lambdaQuery()
							.eq(PointsExchangeRecord::getUserId, userId)
							.eq(PointsExchangeRecord::getPointsGoodsId, goodsId)
							.eq(PointsExchangeRecord::getStatus, "success"));
			if (exchangedCount >= goods.getLimitPerUser()) {
				throw new ArynBusinessException("已达到限购数量");
			}
		}

		// 4. Dubbo调用 IUserPointsApi.deductPoints() 扣积分
		boolean deducted = userPointsApi.deductPoints(userId, goods.getPointsPrice(), goodsId, "POINTS_EXCHANGE");
		if (!deducted) {
			throw new ArynBusinessException("积分扣减失败，积分余额不足");
		}

		// 5. 乐观锁扣库存：UPDATE points_goods SET stock=stock-1 WHERE id=? AND stock>=1
		int updated = pointsGoodsMapper.update(null,
				Wrappers.<PointsGoods>lambdaUpdate()
						.set(PointsGoods::getStock, goods.getStock() - 1)
						.eq(PointsGoods::getId, goodsId)
						.ge(PointsGoods::getStock, 1));
		if (updated <= 0) {
			throw new ArynBusinessException("库存扣减失败，请重试");
		}

		// 6. 生成兑换记录 PointsExchangeRecord（status=success）
		PointsExchangeRecord record = new PointsExchangeRecord();
		record.setUserId(userId);
		record.setPointsGoodsId(goodsId);
		record.setPointsCost(goods.getPointsPrice());
		record.setType(goods.getType());
		record.setStatus("success");
		pointsExchangeRecordMapper.insert(record);

		// 7. 若type=coupon，写入 coupon_user 表发放优惠券
		if ("coupon".equals(goods.getType()) && goods.getTargetId() != null) {
			CouponInfo couponInfo = couponInfoMapper.selectById(goods.getTargetId());
			if (ObjectUtil.isNotNull(couponInfo)) {
				CouponUser couponUser = new CouponUser();
				couponUser.setCouponId(goods.getTargetId());
				couponUser.setUserId(userId);
				couponUser.setStatus("0");
				couponUser.setReceivedTime(LocalDateTime.now());
				couponUser.setValidatTime(couponInfo.getReceiveEndedAt());
				couponUserMapper.insert(couponUser);
			}
			else {
				log.warn("积分兑换优惠券失败，优惠券不存在, couponId={}", goods.getTargetId());
			}
		}

		log.info("积分兑换成功, userId={}, goodsId={}, pointsCost={}", userId, goodsId, goods.getPointsPrice());
		return record;
	}

}
