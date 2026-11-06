package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.dto.DistributionUserRegisterDTO;

/**
 * 分享/扫码绑定分流入口
 * <p>
 * 小程序分享绑定（POST /app/distribution/share/bind）的统一入口：
 * 邀请人为<b>在职销售</b>时走销售归属/待转交认领；邀请人不是销售时保持原分销绑定行为。
 * 任何分支都不把销售会员账号写成客户的分销上级。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface ISalesCustomerBindFacade {

	/**
	 * 绑定分流入口。
	 * @param dto 注册/绑定请求（userId 由调用方置为当前登录会员）
	 */
	void bindFromShare(DistributionUserRegisterDTO dto);

}
