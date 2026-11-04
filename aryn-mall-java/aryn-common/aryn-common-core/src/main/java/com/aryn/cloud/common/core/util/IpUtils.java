package com.aryn.cloud.common.core.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import com.aryn.cloud.common.core.entity.GeoLocation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * IP工具类。
 *
 * <p>职责：取真实客户端 IP、内网 IP 判断，以及归属地解析的统一入口。归属地实现通过
 * {@link IpLocationResolver} SPI 注册：</p>
 * <ul>
 * <li>未引入 {@code aryn-common-ip} 时使用默认的第三方 HTTP 查询
 * （{@link HttpIpLocationResolver}，与原实现行为一致）；</li>
 * <li>引入后由该模块自动配置注册 ip2region 离线库实现（含失败回退与热更新），
 * 本类与所有调用方无需改动。</li>
 * </ul>
 */
@Slf4j
public class IpUtils {

	/** 未知地址 */
	public static final String UNKNOWN = "未知";

	/** 内网地址 */
	public static final String INTRANET = "内网IP";

	// 只接受 IPv4/IPv6 字面量，避免请求头内容被拼进查询地址
	static final Pattern IP_PATTERN = Pattern.compile("[0-9a-fA-F:.]+");

	// 默认实现：第三方 HTTP 查询（保留旧行为，模块未接入或离线库不可用时兜底）
	private static final IpLocationResolver DEFAULT_RESOLVER = new HttpIpLocationResolver();

	private static volatile IpLocationResolver addressResolver = DEFAULT_RESOLVER;

	/**
	 * 取真实客户端 IP：Cloudflare 隧道/代理请求优先用 CF-Connecting-IP，
	 * 其余场景沿用 Hutool 的代理头解析顺序。
	 * @param request 当前请求，可为 null
	 * @return 客户端 IP；无法获取时返回 null
	 */
	public static String getClientIp(HttpServletRequest request) {
		if (request == null) {
			return null;
		}
		String cfConnectingIp = request.getHeader("CF-Connecting-IP");
		if (StringUtils.hasText(cfConnectingIp)) {
			return cfConnectingIp.trim();
		}
		return JakartaServletUtil.getClientIP(request);
	}

	/**
	 * 归属地解析统一入口：返回「省 市」/「内网IP」/「未知」，永不返回 null。
	 * <p>实现由 {@link #registerAddressResolver(IpLocationResolver)} 注册，默认走第三方
	 * HTTP 查询；注册 ip2region 离线实现后自动切换，调用方无需感知。</p>
	 * @param ip 客户端 IP
	 * @return 展示用归属地文本
	 */
	public static String getWhoisAddress(String ip) {
		if (StrUtil.isBlank(ip)) {
			return UNKNOWN;
		}
		String address = ip.trim();
		if (isPrivateIpAddress(address)) {
			return INTRANET;
		}
		if (!IP_PATTERN.matcher(address).matches()) {
			return UNKNOWN;
		}
		IpLocationResolver resolver = addressResolver;
		if (resolver == null) {
			return UNKNOWN;
		}
		try {
			String location = resolver.resolve(address);
			return StrUtil.isBlank(location) ? UNKNOWN : location;
		}
		catch (Exception e) {
			// 归属地只影响展示，任何实现异常都不能影响登录/业务主流程
			log.warn("IP 归属地解析失败 ip={}: {}", address, e.getMessage());
			return UNKNOWN;
		}
	}

	/**
	 * 太平洋通过IP获取省市区（第三方 HTTP，保留旧接口；内网/非法 IP 返回 null）。
	 * @param ip IP 地址
	 * @return 省市区
	 */
	public static GeoLocation getWhoisCity(String ip) {
		if (StrUtil.isBlank(ip) || isPrivateIpAddress(ip)) {
			return null;
		}
		String address = ip.trim();
		if (!IP_PATTERN.matcher(address).matches()) {
			return null;
		}
		return DEFAULT_RESOLVER instanceof HttpIpLocationResolver httpResolver ? httpResolver.resolveCity(address)
				: null;
	}

	/**
	 * 注册归属地解析器（由 {@code aryn-common-ip} 自动配置在启动时调用）。
	 * <p>传 null 恢复默认实现，主要供测试与降级使用。</p>
	 * @param resolver 解析器实现
	 */
	public static void registerAddressResolver(IpLocationResolver resolver) {
		addressResolver = resolver != null ? resolver : DEFAULT_RESOLVER;
	}

	/**
	 * 恢复默认（第三方 HTTP）解析器，测试用。
	 */
	public static void resetAddressResolver() {
		addressResolver = DEFAULT_RESOLVER;
	}

	/**
	 * 当前是否已注册自定义解析器（如 ip2region 离线库）。
	 * @return true 表示已注册
	 */
	public static boolean hasCustomAddressResolver() {
		return addressResolver != DEFAULT_RESOLVER;
	}

	public static boolean isPrivateIpAddress(String ip) {
		if (!StringUtils.hasText(ip)) {
			return false;
		}
		if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1")) {
			return true;
		}
		// Check IPv4 private ranges
		if (ip.startsWith("10.") || ip.startsWith("172.") && isBetween(ip, 16, 31) || ip.startsWith("192.168.")) {
			return true;
		}
		// Check IPv4 link-local range
		if (ip.startsWith("169.254.")) {
			return true;
		}
		// Check for IPv6 unique local addresses
		if (ip.startsWith("fd")) {
			try {
				// Ensure it's a valid fd00::/8 address
				long prefix = Long.parseUnsignedLong(ip.split(":")[0], 16);
				if ((prefix & 0xfff8_0000L) == 0xfd00_0000L) {
					return true;
				}
			}
			catch (NumberFormatException e) {
				// Not a valid fd00::/8 address
			}
		}
		return false;
	}

	private static boolean isBetween(String ipAddress, int lower, int upper) {
		String[] parts = ipAddress.split("\\.");
		if (parts.length != 4) {
			return false;
		}
		try {
			int secondOctet = Integer.parseInt(parts[1]);
			return secondOctet >= lower && secondOctet <= upper;
		}
		catch (NumberFormatException e) {
			return false;
		}
	}

}
