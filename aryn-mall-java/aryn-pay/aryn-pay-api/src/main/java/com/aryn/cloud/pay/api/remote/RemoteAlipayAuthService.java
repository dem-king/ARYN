package com.aryn.cloud.pay.api.remote;

import com.aryn.cloud.pay.api.dto.AlipayAuthCodeExchangeReqDTO;
import com.aryn.cloud.pay.api.dto.AlipayMiniIdentityDTO;

/**
 * 支付宝小程序授权码交换契约。
 *
 * <p>应用私钥与证书只存在于支付材料中，本接口由支付域使用加密材料调用官方 OAuth；
 * 用户域只消费按配置模式选出的买家身份，不接触任何密钥。
 */
public interface RemoteAlipayAuthService {

	/**
	 * 用 my.getAuthCode 的授权码交换买家身份。
	 *
	 * @param request 租户、应用、授权码与显式身份模式
	 * @return 严格按 identityMode 取出的买家身份
	 * @throws com.aryn.cloud.common.security.handler.ArynBusinessException 配置缺失、
	 *         身份模式未配置/不一致、应用与租户不匹配或授权码无效
	 */
	AlipayMiniIdentityDTO exchangeAuthCode(AlipayAuthCodeExchangeReqDTO request);

}
