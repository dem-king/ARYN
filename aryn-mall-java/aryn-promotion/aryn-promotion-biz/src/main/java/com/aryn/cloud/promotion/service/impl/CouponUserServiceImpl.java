
package com.aryn.cloud.promotion.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
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
import com.aryn.cloud.promotion.service.ICouponUserService;

import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponUserServiceImpl extends ServiceImpl<CouponUserMapper, CouponUser> implements ICouponUserService {

	@DubboReference
	private final RemoteMallUserService remoteMallUserService;

	private final CouponInfoMapper couponInfoMapper;

	private final CouponGoodsMapper couponGoodsMapper;

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
		// 校验会员等级限制
		if (StrUtil.isNotBlank(couponInfo.getMemberLevelLimit())) {
			com.aryn.cloud.user.api.vo.UserInfoVO userInfoVO = remoteMallUserService
				.getUserById(couponUser.getUserId());
			if (userInfoVO == null) {
				throw new ArynBusinessException("用户信息不存在");
			}
			// 会员等级限制校验：如果配置了等级限制，用户等级必须在允许列表中
			// 注意：UserInfoVO暂无memberLevelId字段，后续需扩展
			// 此处预留校验逻辑，待UserInfoVO扩展后启用
			log.info("领券会员等级校验, userId={}, memberLevelLimit={}", couponUser.getUserId(),
					couponInfo.getMemberLevelLimit());
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
		couponInfo.setRemainNum(couponInfo.getRemainNum() - 1);
		couponInfo.setAssignCount(couponInfo.getAssignCount() + 1);
		if (couponInfoMapper.updateById(couponInfo) <= 0) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_41000.getCode(),
					MallErrorCodeEnum.ERROR_41000.getMsg());
		}
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
	public CouponUserRespVO getCouponUserById(String id) {
		CouponUser couponUser = this.getById(id);
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
	public List<CouponUser> getExpireCouponList() {
		return baseMapper.selectExpireCouponList();
	}

	@Override
	public List<CouponUserVO> getCountByCouponIds(String[] couponIds) {
		return baseMapper.selectCountByCouponIds(couponIds);
	}

}
