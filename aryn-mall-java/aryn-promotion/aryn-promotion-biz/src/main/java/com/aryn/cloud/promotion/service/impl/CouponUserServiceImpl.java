
package com.aryn.cloud.promotion.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.dto.CouponUserReqDTO;
import com.aryn.cloud.promotion.api.entity.CouponGoods;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import com.aryn.cloud.promotion.api.enums.CouponUserStatusEnum;
import com.aryn.cloud.promotion.api.vo.CouponUserRespVO;
import com.aryn.cloud.promotion.api.vo.CouponUserVO;
import com.aryn.cloud.promotion.mapper.CouponGoodsMapper;
import com.aryn.cloud.promotion.mapper.CouponInfoMapper;
import com.aryn.cloud.promotion.mapper.CouponUserMapper;
import com.aryn.cloud.promotion.event.PromotionMessageCommandPublisher;
import com.aryn.cloud.promotion.service.ICouponUserService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;

@Service
@RequiredArgsConstructor
public class CouponUserServiceImpl extends ServiceImpl<CouponUserMapper, CouponUser> implements ICouponUserService {

	@DubboReference
	private final RemoteMallUserService remoteMallUserService;

	private final CouponInfoMapper couponInfoMapper;

	private final CouponGoodsMapper couponGoodsMapper;

	private final PromotionMessageCommandPublisher messageCommandPublisher;

	@Override
	public IPage<CouponUser> getPage(Page page, CouponUser couponUser) {
		IPage<CouponUser> iPage = baseMapper.selectAdminPage(page, couponUser);
		if (!CollectionUtils.isEmpty(iPage.getRecords())) {
			iPage.getRecords().forEach(v -> {
				v.setUserInfoVO(remoteMallUserService.getUserById(v.getUserId()));
			});
		}
		return iPage;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public CouponUser receive(CouponUser couponUser) {
		couponUser.setTenantId(ArynTenantContextHolder.getTenantId());
		// 查询该优惠券是否限制领取数量
		CouponInfo couponInfo = couponInfoMapper.selectCouponById(couponUser.getCouponId());
		if (ObjectUtil.isNull(couponInfo)) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60062.getCode(),
					MallErrorCodeEnum.ERROR_60062.getMsg());
		}
		if (couponInfo.getRemainNum() <= 0) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60066.getCode(),
					MallErrorCodeEnum.ERROR_60066.getMsg());
		}
		if (couponInfo.getReceiveCount() > 0) {
			// 查询用户已领次数
			long count = baseMapper.selectCount(Wrappers.<CouponUser>lambdaQuery()
				.eq(CouponUser::getCouponId, couponInfo.getId())
				.eq(CouponUser::getUserId, couponUser.getUserId()));
			if (count >= couponInfo.getReceiveCount()) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60063.getCode(),
						MallErrorCodeEnum.ERROR_60063.getMsg());
			}
		}
		couponUser.setStatus(CouponUserStatusEnum.STATUS_0.getCode());
		couponUser.setReceivedTime(LocalDateTime.now());
		couponUser.setValidatTime(couponInfo.getReceiveEndedAt());
		if (!super.save(couponUser)) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_41000.getCode(),
					MallErrorCodeEnum.ERROR_41000.getMsg());
		}
		if (couponInfoMapper.allocateOne(couponInfo.getId()) <= 0) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_41000.getCode(),
					MallErrorCodeEnum.ERROR_41000.getMsg());
		}
		messageCommandPublisher.couponReceived(couponUser, couponInfo);
		return couponUser;
	}

	@Override
	public boolean rollBackCoupon(String couponUserId) {
		CouponUser couponUser = baseMapper.selectById(couponUserId);
		if (CouponUserStatusEnum.STATUS_3.getCode().equals(couponUser.getStatus())) {
			// 比较 当前时间 在 设定的时间 之后 返回的类型是Boolean类型
			if (LocalDateTime.now().isAfter(couponUser.getValidatTime())) {
				couponUser.setStatus(CouponUserStatusEnum.STATUS_0.getCode());
			}
			else {
				couponUser.setStatus(CouponUserStatusEnum.STATUS_2.getCode());
			}
			couponUser.setUsedTime(null);
			return super.updateById(couponUser);
		}
		return Boolean.TRUE;
	}

	@Override
	public IPage<CouponUser> getApiPage(Page page, CouponUser couponUser) {
		return baseMapper.selectApiPage(page, couponUser);
	}

	@Override
	public Boolean updateCouponUserStatus(CouponUserReqDTO couponUserReqDTO) {
		CouponUser couponUser = this.getById(couponUserReqDTO.getId());
		if (Objects.isNull(couponUser)) {
			return Boolean.FALSE;
		}
		// 已使用状态保存使用时间
		if (couponUserReqDTO.getCouponUserStatusEnum().getCode().equals(CouponUserStatusEnum.STATUS_1.getCode())) {
			couponUser.setUsedTime(LocalDateTime.now());
		}
		if (couponUserReqDTO.getCouponUserStatusEnum().getCode().equals(CouponUserStatusEnum.STATUS_0.getCode())) {
			// 待使用状态判断是否过期
			LocalDateTime now = LocalDateTime.now();
			if (couponUser.getValidatTime().isBefore(now)) {
				couponUser.setUsedTime(null);
				couponUser.setStatus(CouponUserStatusEnum.STATUS_2.getCode());
			}
			else {
				couponUser.setUsedTime(null);
				couponUser.setStatus(CouponUserStatusEnum.STATUS_0.getCode());
			}
		}
		else {
			couponUser.setStatus(couponUserReqDTO.getCouponUserStatusEnum().getCode());

		}
		return this.updateById(couponUser);
	}

	@Override
	public CouponUserRespVO getCouponUserById(String id, String userId) {
		CouponUser couponUser = this.getOne(Wrappers.<CouponUser>lambdaQuery()
				.eq(CouponUser::getId, id).eq(CouponUser::getUserId, userId));
		if (Objects.isNull(couponUser)) {
			return null;
		}
		CouponUserRespVO couponUserRespVO = new CouponUserRespVO();
		BeanUtils.copyProperties(couponUser, couponUserRespVO);
		CouponInfo couponInfo = couponInfoMapper.selectById(couponUser.getCouponId());
		if (Objects.isNull(couponInfo)) {
			return null;
		}
		couponUserRespVO.setCouponInfo(couponInfo);
		if (MallEventConstants.USE_RANGE_2.equals(couponInfo.getUseRange())) {
			couponUserRespVO.setCouponGoodsList(couponGoodsMapper
				.selectList(Wrappers.<CouponGoods>lambdaQuery().eq(CouponGoods::getCouponId, couponInfo.getId())));
		}
		return couponUserRespVO;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean reserveCoupon(String id, String userId, String orderId) {
		if (!StringUtils.hasText(id) || !StringUtils.hasText(userId) || !StringUtils.hasText(orderId)) {
			return false;
		}
		return baseMapper.update(null, Wrappers.<CouponUser>lambdaUpdate()
			.eq(CouponUser::getId, id)
			.eq(CouponUser::getUserId, userId)
			.eq(CouponUser::getStatus, CouponUserStatusEnum.STATUS_0.getCode())
			.gt(CouponUser::getValidatTime, LocalDateTime.now())
			.set(CouponUser::getStatus, CouponUserStatusEnum.STATUS_3.getCode())
			.set(CouponUser::getOrderId, orderId)) == 1;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean releaseCoupon(String id, String orderId) {
		if (!StringUtils.hasText(id) || !StringUtils.hasText(orderId)) {
			return false;
		}
		return baseMapper.update(null, Wrappers.<CouponUser>lambdaUpdate()
			.eq(CouponUser::getId, id)
			.eq(CouponUser::getOrderId, orderId)
			.eq(CouponUser::getStatus, CouponUserStatusEnum.STATUS_3.getCode())
			.setSql("status = CASE WHEN validat_time > NOW() THEN '0' ELSE '2' END, order_id = NULL")) == 1;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean grantMemberBenefitCoupon(String couponTemplateId, String userId, String sourceId) {
		String sourceType = "MEMBER_BENEFIT";
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(couponTemplateId) || !StringUtils.hasText(userId)
				|| !StringUtils.hasText(sourceId) || !StringUtils.hasText(tenantId)) {
			throw new ArynBusinessException("会员专属优惠券发放参数不完整");
		}
		long existing = this.count(Wrappers.<CouponUser>lambdaQuery()
				.eq(CouponUser::getUserId, userId)
				.eq(CouponUser::getSourceType, sourceType)
				.eq(CouponUser::getSourceId, sourceId));
		if (existing > 0) {
			return true;
		}
		CouponInfo couponInfo = couponInfoMapper.selectCouponById(couponTemplateId);
		if (couponInfo == null) {
			throw new ArynBusinessException("会员专属优惠券模板不存在");
		}
		CouponUser couponUser = new CouponUser()
				.setId(IdWorker.getIdStr())
				.setCouponId(couponTemplateId)
				.setUserId(userId)
				.setStatus(CouponUserStatusEnum.STATUS_0.getCode())
				.setReceivedTime(LocalDateTime.now())
				.setValidatTime(couponInfo.getReceiveEndedAt())
				.setTenantId(tenantId)
				.setSourceType(sourceType)
				.setSourceId(sourceId);
		if (baseMapper.insertSourceIfAbsent(couponUser) == 0) {
			return true;
		}
		if (couponInfoMapper.allocateOne(couponTemplateId) == 0) {
			throw new ArynBusinessException("会员专属优惠券库存不足");
		}
		return true;
	}

	@Override
	public List<CouponUser> getExpireCouponList() {
		return baseMapper.selectExpireCouponList();
	}

	@Override
	public List<CouponUserVO> getCountByCouponIds(String[] couponIds) {
		return baseMapper.selectCountByCouponIds(couponIds);
	}

}
