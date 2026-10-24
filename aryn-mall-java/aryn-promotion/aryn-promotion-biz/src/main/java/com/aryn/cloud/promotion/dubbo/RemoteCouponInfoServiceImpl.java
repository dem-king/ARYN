package com.aryn.cloud.promotion.dubbo;

import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.remote.RemoteCouponInfoService;
import com.aryn.cloud.promotion.api.vo.CouponTemplateInfoVO;
import com.aryn.cloud.promotion.service.ICouponInfoService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 优惠券模板查询实现。
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteCouponInfoServiceImpl implements RemoteCouponInfoService {

	private final ICouponInfoService couponInfoService;

	@Override
	public CouponTemplateInfoVO getTemplate(String id) {
		if (!StringUtils.hasText(id)) {
			return null;
		}
		CouponInfo couponInfo = couponInfoService.getById(id);
		if (couponInfo == null) {
			return null;
		}
		CouponTemplateInfoVO vo = new CouponTemplateInfoVO();
		BeanUtils.copyProperties(couponInfo, vo);
		return vo;
	}

}
