package com.aryn.cloud.common.core.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.entity.GeoLocation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * IP工具类
 *
 * @author 雨滴kian
 * @date 2022/6/10
 */
@Slf4j
public class IpUtils {

	// ip查询地址（源站已拒绝 HTTP 访问，返回 403，只能走 HTTPS）
	private static final String IP_URL = "https://whois.pconline.com.cn/ipJson.jsp";

	private static final String USER_AGENT = "Mozilla/4.0 compatible; MSIE 6.0; Windows NT 5.1;DigExt";

	// 未知地址
	private static final String UNKNOWN = "未知";

	// 内网地址
	private static final String INTRANET = "内网IP";

	// 查询超时：登录与操作日志是同步链路，不能被第三方接口长时间拖住
	private static final int QUERY_TIMEOUT_MILLIS = 1500;

	// 成功结果缓存 30 分钟；失败只缓存 1 分钟，接口恢复后能尽快重新解析
	private static final long SUCCESS_CACHE_MILLIS = 30 * 60 * 1000L;

	private static final long FAILURE_CACHE_MILLIS = 60 * 1000L;

	private static final int CACHE_LIMIT = 1024;

	// 只接受 IPv4/IPv6 字面量，避免请求头内容被拼进查询地址
	private static final Pattern IP_PATTERN = Pattern.compile("[0-9a-fA-F:.]+");

	private static final Map<String, CachedAddress> CACHE = new ConcurrentHashMap<>();

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
	 * 太平洋通过IP获取地址信息
	 * @param ip
	 * @return
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
		long now = System.currentTimeMillis();
		CachedAddress cached = CACHE.get(address);
		if (cached != null && cached.expireAt() > now) {
			return cached.location();
		}
		String location = parseAddress(query(address));
		cache(address, location, now);
		return location;
	}

	/**
	 * 太平洋通过IP获取省市区
	 * @param ip
	 * @return
	 */
	public static GeoLocation getWhoisCity(String ip) {
		if (isPrivateIpAddress(ip)) {
			return null;
		}
		JSONObject result = query(ip);
		if (result == null) {
			return null;
		}
		return new GeoLocation(result.getStr("pro"), result.getStr("city"), result.getStr("region"));
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

	/**
	 * 查询归属地原始响应；网络异常/空响应返回 null（调用方降级为「未知」）。
	 */
	private static JSONObject query(String ip) {
		try {
			String url = IP_URL + "?ip=" + ip + "&json=true";
			String returnStr = HttpUtil.createGet(url)
				.header("User-Agent", USER_AGENT)
				.setConnectionTimeout(QUERY_TIMEOUT_MILLIS)
				.setReadTimeout(QUERY_TIMEOUT_MILLIS)
				.execute()
				.body();
			if (StrUtil.isBlank(returnStr)) {
				return null;
			}
			return JSONUtil.parseObj(returnStr);
		}
		catch (Exception e) {
			log.warn("IP 归属地查询失败 ip={}: {}", ip, e.getMessage());
			return null;
		}
	}

	/**
	 * 归属地展示：`省 市`；接口对海外 IP 只给 addr（如「 美国」），此时直接用 addr。
	 */
	static String parseAddress(JSONObject result) {
		if (result == null) {
			return UNKNOWN;
		}
		String province = StrUtil.trimToEmpty(result.getStr("pro"));
		String city = StrUtil.trimToEmpty(result.getStr("city"));
		String location = (province + " " + city).trim();
		if (StrUtil.isBlank(location)) {
			location = StrUtil.trimToEmpty(result.getStr("addr"));
		}
		return StrUtil.isBlank(location) ? UNKNOWN : location;
	}

	private static void cache(String ip, String location, long now) {
		if (CACHE.size() >= CACHE_LIMIT) {
			CACHE.entrySet().removeIf(entry -> entry.getValue().expireAt() <= now);
			if (CACHE.size() >= CACHE_LIMIT) {
				CACHE.clear();
			}
		}
		long ttl = UNKNOWN.equals(location) ? FAILURE_CACHE_MILLIS : SUCCESS_CACHE_MILLIS;
		CACHE.put(ip, new CachedAddress(location, now + ttl));
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

	private record CachedAddress(String location, long expireAt) {
	}

}
