
package com.aryn.cloud.upms.service;
/**
 * 短信
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
public interface ISysSmsService {

	/**
	 * 发送短信验证码
	 * @param mobile 手机号
	 * @param type 验证码类型 注册、登录、重置密码、修改手机号
	 * @param clientIp 客户端IP，用于发送频控；可为空（为空时跳过IP维度限流）
	 * @return
	 */
	Boolean sendSmsCode(String mobile, String type, String clientIp);

}
