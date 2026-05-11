
package com.aryn.cloud.promotion.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import com.aryn.cloud.promotion.api.enums.CouponUserStatusEnum;
import com.aryn.cloud.promotion.service.ICouponUserService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 用户优惠券定时任务
 *
 * @author 雨滴kian
 * @date 2022/11/02
 */
@Component
@RequiredArgsConstructor
public class CouponUserJobHandler {

	private final ICouponUserService couponUserService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	/**
	 * 扫描过期的优惠券
	 *
	 * @author 雨滴kian
	 * @date 2022/11/02
	 * @return: void
	 */
	@XxlJob("scanExpiredCouponsJobHandler")
	public void scanExpiredCouponsJobHandler() throws Exception {
		XxlJobHelper.log("扫描过期的优惠券, upCouponUserStatusJobHandler.");
		List<SysTenant> listSysTenant = remoteTenantService.list();
		if (!CollectionUtils.isEmpty(listSysTenant)) {
			listSysTenant.forEach(sysTenant -> {
				ArynTenantContextHolder.setTenantId(sysTenant.getId());
				List<CouponUser> couponUserList = couponUserService.getExpireCouponList();
				couponUserList.forEach(couponUser -> {
					couponUser.setStatus(CouponUserStatusEnum.STATUS_2.getCode());
					couponUser.setUpdateBy("job");
					couponUserService.updateById(couponUser);
				});
				ArynTenantContextHolder.removeTenantId();
			});
		}
		// default success
	}

}
