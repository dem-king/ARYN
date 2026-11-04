package com.aryn.cloud.common.ip.provider;

import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.util.IpUtils;

import java.util.Set;

/**
 * ip2region region 文本 → 展示归属地的格式化。
 *
 * <p>ip2region v4 数据的 region 格式为
 * {@code 国家|省份|城市|ISP|iso-alpha2-code}（自 3.x 数据标准起），例如：</p>
 * <ul>
 * <li>{@code 中国|浙江省|杭州市|阿里|CN} → 浙江省 杭州市</li>
 * <li>{@code 中国|北京市|北京市|0|CN} → 北京市（省==市去重）</li>
 * <li>{@code United States|California|0|Google LLC|US} → United States（海外只展示国家，与
 * 第三方接口返回英文国家名一致）</li>
 * <li>{@code Reserved|Reserved|Reserved|0|0} → 未知（未分配/保留地址）</li>
 * </ul>
 */
public final class IpRegionTextFormatter {

	private static final String SEPARATOR = "|";

	/** 数据里表示「空」的占位值 */
	private static final String EMPTY_VALUE = "0";

	private static final String CHINA = "中国";

	/** 无法用于展示的国家值：空 / 0 / Reserved（含大小写） */
	private static final Set<String> UNUSABLE_COUNTRY = Set.of("", EMPTY_VALUE, "reserved", "unknown", "n/a");

	private IpRegionTextFormatter() {
	}

	/**
	 * 把 ip2region 的 region 文本转成展示归属地。
	 * @param region ip2region search 返回的原始 region，可为 null
	 * @return 展示文本，无法解析时返回 {@link IpUtils#UNKNOWN}
	 */
	public static String format(String region) {
		if (StrUtil.isBlank(region)) {
			return IpUtils.UNKNOWN;
		}
		String[] parts = region.split("\\" + SEPARATOR, -1);
		if (parts.length < 3) {
			return IpUtils.UNKNOWN;
		}
		String country = clean(parts[0]);
		if (isUnusableCountry(country)) {
			return IpUtils.UNKNOWN;
		}
		// 海外数据只保证国家可信（省市字段为英文/可能为 0），与旧 HTTP 实现的「美国」口径对齐
		if (!CHINA.equals(country)) {
			return country;
		}
		String province = clean(parts[1]);
		String city = clean(parts[2]);
		if (StrUtil.isBlank(province) && StrUtil.isBlank(city)) {
			return IpUtils.UNKNOWN;
		}
		if (StrUtil.isBlank(province)) {
			return city;
		}
		// 直辖市数据省==市（如「北京市 北京市」），去重展示
		if (StrUtil.isBlank(city) || province.equals(city)) {
			return province;
		}
		return province + " " + city;
	}

	/**
	 * 判断 region 是否可解析（供调用方快速判断，不重复格式化）。
	 * @param location 格式化后的展示文本
	 * @return true 表示可用
	 */
	public static boolean isResolved(String location) {
		return StrUtil.isNotBlank(location) && !IpUtils.UNKNOWN.equals(location) && !IpUtils.INTRANET.equals(location);
	}

	private static boolean isUnusableCountry(String country) {
		return UNUSABLE_COUNTRY.contains(StrUtil.nullToEmpty(country).toLowerCase());
	}

	private static String clean(String value) {
		String trimmed = StrUtil.trimToEmpty(value);
		if (EMPTY_VALUE.equals(trimmed)) {
			return StrUtil.EMPTY;
		}
		return trimmed;
	}

}
