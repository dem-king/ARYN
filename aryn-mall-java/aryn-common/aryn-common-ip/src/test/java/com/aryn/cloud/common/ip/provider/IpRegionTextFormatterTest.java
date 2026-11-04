package com.aryn.cloud.common.ip.provider;

import com.aryn.cloud.common.core.util.IpUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ip2region region 文本格式化：省/市拼接、直辖市去重、海外只展示国家、保留地址降级。
 */
class IpRegionTextFormatterTest {

	@Test
	void shouldFormatProvinceAndCity() {
		assertThat(IpRegionTextFormatter.format("中国|浙江省|杭州市|阿里|CN")).isEqualTo("浙江省 杭州市");
	}

	@Test
	void shouldDedupeMunicipality() {
		assertThat(IpRegionTextFormatter.format("中国|北京市|北京市|0|CN")).isEqualTo("北京市");
		assertThat(IpRegionTextFormatter.format("中国|上海市|上海市|联通|CN")).isEqualTo("上海市");
	}

	@Test
	void shouldFallbackToProvinceWhenCityMissing() {
		assertThat(IpRegionTextFormatter.format("中国|新疆|0|0|CN")).isEqualTo("新疆");
		assertThat(IpRegionTextFormatter.format("中国|0|南京市|0|CN")).isEqualTo("南京市");
		assertThat(IpRegionTextFormatter.format("中国|香港特别行政区|0|电讯盈科|CN")).isEqualTo("香港特别行政区");
	}

	@Test
	void shouldReturnCountryForOverseas() {
		assertThat(IpRegionTextFormatter.format("United States|California|0|Google LLC|US"))
			.isEqualTo("United States");
		assertThat(IpRegionTextFormatter.format("Japan|Tokyo|0|0|JP")).isEqualTo("Japan");
	}

	@Test
	void shouldReturnUnknownForUnusableRegion() {
		assertThat(IpRegionTextFormatter.format(null)).isEqualTo(IpUtils.UNKNOWN);
		assertThat(IpRegionTextFormatter.format("")).isEqualTo(IpUtils.UNKNOWN);
		assertThat(IpRegionTextFormatter.format("Reserved|Reserved|Reserved|0|0")).isEqualTo(IpUtils.UNKNOWN);
		assertThat(IpRegionTextFormatter.format("中国|0|0|0|CN")).isEqualTo(IpUtils.UNKNOWN);
		assertThat(IpRegionTextFormatter.format("only-one-field")).isEqualTo(IpUtils.UNKNOWN);
	}

	@Test
	void shouldJudgeResolvedLocation() {
		assertThat(IpRegionTextFormatter.isResolved("浙江省 杭州市")).isTrue();
		assertThat(IpRegionTextFormatter.isResolved(IpUtils.UNKNOWN)).isFalse();
		assertThat(IpRegionTextFormatter.isResolved(IpUtils.INTRANET)).isFalse();
		assertThat(IpRegionTextFormatter.isResolved(null)).isFalse();
	}

}
