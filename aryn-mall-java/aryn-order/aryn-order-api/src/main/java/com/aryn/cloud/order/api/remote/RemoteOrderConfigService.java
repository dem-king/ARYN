package com.aryn.cloud.order.api.remote;

/**
 * 订单配置 Dubbo 远程服务接口
 *
 * <p>供其它业务域读取订单域全局配置。当前场景：余额充值需要与订单支付
 * 使用同一个支付回调域名（支付模块会拼上自身前缀与租户信息），
 * 而 order_config 归订单域所有，跨域只能走 Dubbo。
 */
public interface RemoteOrderConfigService {

	/**
	 * 获取当前租户生效的订单配置回调地址
	 * @return 回调基础地址；未配置或不存在返回 null
	 */
	String getNotifyUrl();
}
