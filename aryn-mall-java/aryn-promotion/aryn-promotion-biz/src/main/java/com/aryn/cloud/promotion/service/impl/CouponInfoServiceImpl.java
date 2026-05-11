
package com.aryn.cloud.promotion.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.entity.CouponGoods;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import com.aryn.cloud.promotion.mapper.CouponInfoMapper;
import com.aryn.cloud.promotion.mapper.CouponUserMapper;
import com.aryn.cloud.promotion.service.ICouponGoodsService;
import com.aryn.cloud.promotion.service.ICouponInfoService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;

@Service
@AllArgsConstructor
public class CouponInfoServiceImpl extends ServiceImpl<CouponInfoMapper, CouponInfo> implements ICouponInfoService {

	private final ICouponGoodsService couponGoodsService;

	private final CouponUserMapper couponUserMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean save(CouponInfo entity) {
		if (!super.save(entity)) {
			throw new ArynBusinessException("保存失败");
		}
		if (MallEventConstants.USE_RANGE_2.equals(entity.getUseRange())) {
			List<CouponGoods> goodsList = entity.getCouponGoodsList();
			if (goodsList.isEmpty()) {
				throw new ArynBusinessException("指定商品不能为空");
			}
			goodsList.forEach(couponGoods -> {
				couponGoods.setCouponId(entity.getId());
			});
			couponGoodsService.saveBatch(goodsList);
		}

		return Boolean.TRUE;
	}

	@Override
	public CouponInfo getCouponById(String id) {
		CouponInfo couponInfo = baseMapper.selectById(id);
		if (ObjectUtil.isNotNull(couponInfo) && MallEventConstants.USE_RANGE_2.equals(couponInfo.getUseRange())) {
			couponInfo.setCouponGoodsList(couponGoodsService.getByCouponId(couponInfo.getId()));
		}
		return couponInfo;
	}

	@Override
	public IPage<CouponInfo> getPage(Page page, CouponInfo couponInfo, CouponGoods couponGoods, CouponUser couponUser) {
		IPage<CouponInfo> iPage = baseMapper.selectCouponPage(page, couponInfo, couponGoods, couponUser);
		if (CollectionUtils.isEmpty(iPage.getRecords())) {
			return iPage;
		}
		iPage.getRecords().forEach(v -> {
			if (Objects.nonNull(couponUser.getUserId())) {
				// 查询领取数量
				Long count = couponUserMapper.selectCount(Wrappers.<CouponUser>lambdaQuery()
					.eq(CouponUser::getCouponId, v.getId())
					.eq(CouponUser::getUserId, couponUser.getUserId()));
				v.setUserReceiveCount(count);
			}
		});
		return iPage;
	}

	@Override
	public IPage<CouponInfo> getAdminPage(Page page, CouponInfo couponInfo) {

		return baseMapper.selectAdminPage(page, couponInfo);
	}

}
