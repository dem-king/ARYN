package com.aryn.cloud.user.service;

import com.aryn.cloud.user.api.vo.SalesQrcodeVO;

/**
 * 销售个人推广小程序码
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface ISalesQrcodeService {

	/**
	 * 生成/读取当前会员的销售推广小程序码。
	 * <p>
	 * 仅在职销售本人可调用；成功结果 Redis 缓存 7 天，微信接口失败结果短缓存 5 分钟，
	 * 避免前端重试打爆微信接口。
	 * @param tenantId 当前租户ID
	 * @param memberUserId 当前登录会员ID（销售本人的 member_user_id）
	 * @return 推广码（data URI + page + scene）
	 */
	SalesQrcodeVO getQrcode(String tenantId, String memberUserId);

}
