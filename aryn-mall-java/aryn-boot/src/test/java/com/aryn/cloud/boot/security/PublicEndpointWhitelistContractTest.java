package com.aryn.cloud.boot.security;

import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 免登白名单契约测试。
 *
 * <p>背景（真实缺陷）：小程序登录成功后点首页金刚区分类，立刻被提示「登录已过期」
 * 并跳回登录页。根因不是登录态问题 —— 金刚区落地页（goods-list）会以**免登**方式
 * 请求商品品牌列表，而该接口没登记进免登白名单，服务端于是返回 401；
 * 前端把 401 一律当作登录态失效，清 token 跳登录页。
 *
 * <p>这类漏配的隐蔽之处：接口在前端已用 `skipToken: true` 声明为公开，后端白名单
 * 是否覆盖却没人核对；编译、类型检查、既有测试全绿，只有真机点金刚区才暴露。
 * 因此这里把「前端声明公开的接口」与「后端免登白名单」绑成一条契约：
 *
 * <ol>
 *   <li>前端每个 `skipToken` 接口在 boot 白名单里必须有对应条目；</li>
 *   <li>需要登录的写接口不得因白名单过宽被顺带放开；</li>
 *   <li>cloud 网关白名单与 boot 保持一致（双模式强制要求）。</li>
 * </ol>
 *
 * <p>匹配语义：Spring 环境下 Sa-Token 经 {@code SaPathMatcherHolder} 使用
 * {@link AntPathMatcher}，故 {@code /a/b/**} 同时匹配 {@code /a/b} 与 {@code /a/b/...}。
 */
class PublicEndpointWhitelistContractTest {

	private static final AntPathMatcher MATCHER = new AntPathMatcher();

	/**
	 * 前端声明 `skipToken: true` 的接口（去微服务域后的 boot 形态）。
	 *
	 * <p>与 aryn-mall-uniapp 的 api 层逐条对应；新增免登接口时必须同时更新
	 * 本清单与后端白名单，否则本测试失败 —— 这正是它的目的。
	 */
	private static final List<String> FRONTEND_PUBLIC_PATHS = List.of(
			// 登录/短信
			"/toc-token/ma/login",
			"/toc-token/sms/login",
			"/toc-token/password/login",
			"/toc-token/ma/phone/login",
			"/token/delivery-login",
			"/sms/1/13800000000",
			// 租户门店信息
			"/app/tenant/shop-info",
			// 商品域：分类树 / 品牌列表与品牌筛选项 / 商品列表与详情 / 快捷加购
			"/app/goodscategory/tree",
			"/app/goodsbrand/list",
			"/app/goodsbrand/filter-list",
			"/app/goodsspu/page",
			"/app/goodsspu/1912867577569386497",
			"/app/goodsspu/list/1,2",
			"/app/goodsspu/hot-search/top10",
			"/app/goodsspu/quick-cart/123",
			// 营销域：装修页 / 商城默认主题换肤 / 拼团 / 优惠券 / 秒杀 / 折扣
			"/app/pagedesign",
			"/app/pagedesign/2102572931049308162",
			"/app/pagedesign/mall-theme",
			"/app/groupbuy/activity/page",
			"/app/couponinfo/page",
			"/app/seckill/sessions",
			"/app/seckill/sessions/1/goods",
			"/app/seckill/goods/sku1",
			"/app/discount/activities",
			// 本机存储文件（装修图标等）
			"/file/local/1590229800633634816/icon.jpg");

	/**
	 * 必须**保持**登录校验的接口：白名单过宽（如写成 `/app/**`）会把它们顺带放开，
	 * 属越权风险，同样要拦。
	 */
	private static final List<String> MUST_STAY_PRIVATE_PATHS = List.of(
			"/app/seckill/order",
			"/app/discount/goods/sku1",
			"/app/couponuser/page",
			"/app/collect",
			"/app/footprint",
			"/app/userinfo",
			"/app/distribution/center",
			"/mall-order/app/shopping-cart/page");

	/**
	 * yaml 里的白名单条目行（`      - /app/xxx`）。
	 *
	 * <p>必须带 {@link Pattern#MULTILINE}：Java 的 {@code ^}/{@code $} 默认只在整段文本
	 * 首尾匹配，不加标记会一条都匹配不到（本测试第一版就因此把白名单解析成空列表）。
	 */
	private static final Pattern YAML_URL_ENTRY =
			Pattern.compile("^\\s*-\\s+(/[^\\s#]+)\\s*$", Pattern.MULTILINE);

	private final Path projectRoot = findProjectRoot();

	@Test
	void bootWhitelistCoversEveryFrontendPublicEndpoint() throws IOException {
		List<String> whitelist = bootWhitelist();
		List<String> uncovered = new ArrayList<>();
		for (String path : FRONTEND_PUBLIC_PATHS) {
			if (!matchesAny(whitelist, path)) {
				uncovered.add(path);
			}
		}
		assertThat(uncovered)
				.as("以下免登接口未登记进 aryn-boot/application.yml 的 secure.ignore.urls："
						+ "前端以 skipToken 调用它们，漏配会导致登录用户被误判为登录过期")
				.isEmpty();
	}

	@Test
	void bootWhitelistKeepsAuthenticatedEndpointsPrivate() throws IOException {
		List<String> whitelist = bootWhitelist();
		List<String> leaked = new ArrayList<>();
		for (String path : MUST_STAY_PRIVATE_PATHS) {
			if (matchesAny(whitelist, path)) {
				leaked.add(path);
			}
		}
		assertThat(leaked)
				.as("免登白名单过宽，以下需登录接口被顺带放开")
				.isEmpty();
	}

	@Test
	void cloudGatewayWhitelistCoversTheSamePublicEndpointsWhereScoped() throws IOException {
		// cloud 由网关统一鉴权，路径带微服务域前缀（/product、/promotion ...）。
		// 只校验网关未用域级通配覆盖的部分：promotion 域是逐条列举的。
		String seed = Files.readString(projectRoot.resolve("db/cloud/3aryn_nacos.sql"));
		String gatewayContent = gatewayConfigContent(seed);
		List<String> whitelist = yamlUrlEntries(gatewayContent);

		// 网关白名单缩进为 4 空格（`    - /xxx`），boot 的是 6 空格，正则两种都要认
		assertThat(whitelist).as("cloud 网关白名单解析结果不应为空").isNotEmpty();
		// /product/app/** 已覆盖全部商品域公开读接口
		assertThat(matchesAny(whitelist, "/product/app/goodsbrand/list"))
				.as("cloud 网关应覆盖商品域公开读接口，实际解析到：%s", whitelist).isTrue();

		List<String> uncovered = new ArrayList<>();
		for (String path : List.of(
				"/promotion/app/seckill/sessions",
				"/promotion/app/seckill/sessions/1/goods",
				"/promotion/app/seckill/goods/sku1",
				"/promotion/app/discount/activities")) {
			if (!matchesAny(whitelist, path)) {
				uncovered.add(path);
			}
		}
		assertThat(uncovered)
				.as("以下 promotion 公开读接口未登记进 cloud 网关白名单（db/cloud/3aryn_nacos.sql）")
				.isEmpty();
	}

	@Test
	void cloudIncrementScriptTargetsTheGatewayWhitelist() throws IOException {
		// 存量的 cloud 库靠增量脚本升级，脚本必须真的改 aryn-gateway-dev.yml
		Path script = projectRoot.resolve("db/cloud/88public_goods_read_nacos_config.sql");
		assertThat(script).exists();
		String sql = Files.readString(script);
		assertThat(sql).contains("'aryn-gateway-dev.yml'");
		assertThat(sql).contains("/promotion/app/seckill/sessions/**");
		assertThat(sql).contains("/promotion/app/seckill/goods/**");
		assertThat(sql).contains("/promotion/app/discount/activities/**");
		// 幂等：必须维护 md5，且不得用域级通配一把放开
		assertThat(sql).contains("`md5` = MD5(@gateway_content)");
		assertThat(sql).doesNotContain("'    - /promotion/app/**");
	}

	private List<String> bootWhitelist() throws IOException {
		return yamlUrlEntries(Files.readString(
				projectRoot.resolve("aryn-boot/src/main/resources/application.yml")));
	}

	/** 取 yaml 里所有 `- /xxx` 形式的白名单条目（boot 的 secure.ignore 即此形态） */
	private static List<String> yamlUrlEntries(String yaml) {
		List<String> urls = new ArrayList<>();
		Matcher matcher = YAML_URL_ENTRY.matcher(yaml);
		while (matcher.find()) {
			urls.add(matcher.group(1));
		}
		return urls;
	}

	private static boolean matchesAny(List<String> patterns, String path) {
		for (String pattern : patterns) {
			if (MATCHER.match(pattern, path)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 从 3aryn_nacos.sql 的 INSERT 语句里取出 aryn-gateway-dev.yml 的 content。
	 *
	 * <p>与 TenantConfigurationConsistencyTest 的解析口径保持一致：手工扫描单引号，
	 * 不用正则 —— 该文件把配置存成 MySQL 字符串字面量（换行为 \\n 转义），
	 * 正则里的反斜杠要再转义一层，极易写错。
	 */
	private static String gatewayConfigContent(String sql) {
		for (String line : sql.split("\n", -1)) {
			if (!line.startsWith("INSERT INTO `config_info` VALUES (")
					|| !line.contains("'aryn-gateway-dev.yml'")) {
				continue;
			}
			List<String> values = parseMysqlStringValues(line);
			// INSERT 的 id 是未加引号的数字，不在字符串值里，因此下标相对 SQL 列整体前移 1：
			// get(0)=data_id get(1)=group_id get(2)=content get(3)=md5
			if (values.size() >= 3) {
				// unescapeMysql 已把字面量里的 \n 还原成真实换行
				return unescapeMysql(values.get(2));
			}
		}
		throw new IllegalStateException("3aryn_nacos.sql 中未找到 aryn-gateway-dev.yml 配置");
	}

	private static List<String> parseMysqlStringValues(String line) {
		List<String> values = new ArrayList<>();
		int index = line.indexOf('(');
		while (index < line.length()) {
			int quote = line.indexOf('\'', index);
			if (quote < 0) {
				break;
			}
			StringBuilder builder = new StringBuilder();
			int cursor = quote + 1;
			while (cursor < line.length()) {
				char current = line.charAt(cursor);
				if (current == '\\' && cursor + 1 < line.length()) {
					builder.append(current).append(line.charAt(cursor + 1));
					cursor += 2;
					continue;
				}
				if (current == '\'') {
					break;
				}
				builder.append(current);
				cursor++;
			}
			values.add(builder.toString());
			index = cursor + 1;
		}
		return values;
	}

	/** 还原 MySQL 字符串字面量里的转义（\n / \t / \r / \' / \\ 等） */
	private static String unescapeMysql(String value) {
		StringBuilder result = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char current = value.charAt(i);
			if (current != '\\' || i + 1 >= value.length()) {
				result.append(current);
				continue;
			}
			char next = value.charAt(++i);
			switch (next) {
				case 'n' -> result.append('\n');
				case 't' -> result.append('\t');
				case 'r' -> result.append('\r');
				case '0' -> result.append('\0');
				case 'b' -> result.append('\b');
				case 'Z' -> result.append('\u001a');
				default -> result.append(next);
			}
		}
		return result.toString();
	}

	private static Path findProjectRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null && !Files.isDirectory(current.resolve("db/cloud"))) {
			current = current.getParent();
		}
		if (current == null) {
			throw new IllegalStateException("未找到包含 db/cloud 的项目根目录");
		}
		return current;
	}
}
