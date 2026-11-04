package com.aryn.cloud.common.core.util;

/**
 * IP 归属地解析器 SPI。
 *
 * <p>默认实现是第三方 HTTP 查询（{@link HttpIpLocationResolver}）；引入
 * {@code aryn-common-ip} 后由自动配置注册 ip2region 离线库实现，业务代码继续通过
 * {@link IpUtils#getWhoisAddress(String)} 调用，无需感知底层实现。</p>
 *
 * <p>约定：返回面向展示的地点文本（如「浙江省 杭州市」「内网IP」），无法解析时返回
 * {@link IpUtils#UNKNOWN}；实现方不应抛异常（{@link IpUtils} 也会兜底捕获并降级）。</p>
 */
@FunctionalInterface
public interface IpLocationResolver {

	/**
	 * 解析 IP 归属地。
	 * @param ip 已去空格、格式合法的 IP（IPv4/IPv6 字面量）
	 * @return 展示文本，不能为 null
	 */
	String resolve(String ip);

}
