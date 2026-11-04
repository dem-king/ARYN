package com.aryn.cloud.common.core.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.entity.GeoLocation;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 第三方 HTTP 归属地解析（太平洋网络 whois 接口）。
 *
 * <p>这是 {@link IpUtils} 的默认/兜底实现，从原 {@code IpUtils} 中抽取，行为保持不变：</p>
 * <ul>
 * <li>源站拒绝 HTTP（403），只能走 HTTPS，响应为 GBK；</li>
 * <li>查询超时 1.5s，登录与操作日志是同步链路，不能被第三方接口长时间拖住；</li>
 * <li>成功结果缓存 30 分钟、失败只缓存 1 分钟，接口恢复后能尽快重新解析；</li>
 * <li>返回「省 市」，海外 IP 只有 addr 时直接用 addr（如「美国」）。</li>
 * </ul>
 *
 * <p><b>注意</b>：该接口没有公开的服务条款/商用授权与 SLA（并发一高就 503），
 * 生产环境应使用 {@code aryn-common-ip} 的 ip2region 离线库，本类仅作可选兜底。</p>
 */
@Slf4j
public class HttpIpLocationResolver implements IpLocationResolver {

	// ip查询地址（源站已拒绝 HTTP 访问，返回 403，只能走 HTTPS）
	private static final String IP_URL = "https://whois.pconline.com.cn/ipJson.jsp";

	private static final String USER_AGENT = "Mozilla/4.0 compatible; MSIE 6.0; Windows NT 5.1;DigExt";

	// 查询超时：登录与操作日志是同步链路，不能被第三方接口长时间拖住
	private static final int QUERY_TIMEOUT_MILLIS = 1500;

	// 成功结果缓存 30 分钟；失败只缓存 1 分钟，接口恢复后能尽快重新解析
	private static final long SUCCESS_CACHE_MILLIS = 30 * 60 * 1000L;

	private static final long FAILURE_CACHE_MILLIS = 60 * 1000L;

	private static final int CACHE_LIMIT = 1024;

	// 只接受 IPv4/IPv6 字面量，避免请求头内容被拼进查询地址
	static final Pattern IP_PATTERN = Pattern.compile("[0-9a-fA-F:.]+");

	private final Map<String, CachedAddress> cache = new ConcurrentHashMap<>();

	@Override
	public String resolve(String ip) {
		if (StrUtil.isBlank(ip)) {
			return IpUtils.UNKNOWN;
		}
		String address = ip.trim();
		if (IpUtils.isPrivateIpAddress(address)) {
			return IpUtils.INTRANET;
		}
		if (!IP_PATTERN.matcher(address).matches()) {
			return IpUtils.UNKNOWN;
		}
		long now = System.currentTimeMillis();
		CachedAddress cached = cache.get(address);
		if (cached != null && cached.expireAt() > now) {
			return cached.location();
		}
		String location = parseAddress(query(address));
		cache(address, location, now);
		return location;
	}

	/**
	 * 太平洋通过IP获取省市区；内网/非法 IP 返回 null。
	 * @param ip IP 地址
	 * @return 省市区，查询失败时返回 null
	 */
	public GeoLocation resolveCity(String ip) {
		if (StrUtil.isBlank(ip) || IpUtils.isPrivateIpAddress(ip)) {
			return null;
		}
		JSONObject result = query(ip.trim());
		if (result == null) {
			return null;
		}
		return new GeoLocation(result.getStr("pro"), result.getStr("city"), result.getStr("region"));
	}

	/**
	 * 查询归属地原始响应；网络异常/空响应返回 null（调用方降级为「未知」）。
	 */
	private JSONObject query(String ip) {
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
			return IpUtils.UNKNOWN;
		}
		String province = StrUtil.trimToEmpty(result.getStr("pro"));
		String city = StrUtil.trimToEmpty(result.getStr("city"));
		String location = (province + " " + city).trim();
		if (StrUtil.isBlank(location)) {
			location = StrUtil.trimToEmpty(result.getStr("addr"));
		}
		return StrUtil.isBlank(location) ? IpUtils.UNKNOWN : location;
	}

	private void cache(String ip, String location, long now) {
		if (cache.size() >= CACHE_LIMIT) {
			cache.entrySet().removeIf(entry -> entry.getValue().expireAt() <= now);
			if (cache.size() >= CACHE_LIMIT) {
				cache.clear();
			}
		}
		long ttl = IpUtils.UNKNOWN.equals(location) ? FAILURE_CACHE_MILLIS : SUCCESS_CACHE_MILLIS;
		cache.put(ip, new CachedAddress(location, now + ttl));
	}

	private record CachedAddress(String location, long expireAt) {
	}

}
