package com.aryn.cloud.boot.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C 端接口鉴权契约测试。
 *
 * <p>背景（真实缺陷）：小程序签到页调用了管理端接口 {@code /signinrecord/page}，
 * 该类标着 {@code @SaCheckPermission("user:signinrecord:page")}。C 端登录
 * （{@code TocLoginService}）从不给 token 灌权限 —— 只有管理端 {@code LoginService}
 * 和配送端 {@code DeliveryAuthService} 才 {@code setPermissions} ——
 * 于是 C 端用户拿空权限列表去比对，服务端一律返回 403。
 *
 * <p>前端的 401/403 混判又把它报成「登录已过期」并清 token，
 * 表现为「重新登录也一直在过期」，极难从现象反推根因。
 *
 * <p>因此把「C 端接口不得要求管理端权限」绑成一条契约：
 * {@code controller/app} 包下的任一 handler 出现 {@code @SaCheckPermission}
 * 或 {@code @SaCheckRole}，本测试失败。
 *
 * <p>C 端自己的鉴权方式是从登录态取用户：
 * {@code SecurityUtils.getUser().getUserId()}，登录校验由全局过滤器统一负责。
 */
class AppControllerPermissionContractTest {

	/** 匹配方法级（非类级）的权限/角色注解 */
	private static final Pattern PERMISSION_ANNOTATION =
			Pattern.compile("@SaCheck(Permission|Role)\\s*\\(");

	private final Path projectRoot = findProjectRoot();

	@Test
	void appControllersNeverRequireAdminPermissions() throws IOException {
		List<String> offenders = new ArrayList<>();

		for (Path controller : appControllers()) {
			String content = Files.readString(controller);
			Matcher matcher = PERMISSION_ANNOTATION.matcher(content);
			while (matcher.find()) {
				// 记录行号，便于定位
				int line = (int) content.substring(0, matcher.start()).chars()
						.filter(ch -> ch == '\n').count() + 1;
				offenders.add(projectRoot.relativize(controller) + ":" + line);
			}
		}

		assertThat(offenders)
				.as("C 端接口（controller/app 包）不得要求管理端权限：C 端 token 没有 permissions，"
						+ "加了权限注解必然 403，且会被前端误报为登录过期。"
						+ "C 端应从登录态取用户（SecurityUtils.getUser().getUserId()）")
				.isEmpty();
	}

	@Test
	void appControllersExistAndAreScanned() throws IOException {
		// 防呆：路径写错会让上面的测试扫到 0 个文件而假绿
		assertThat(appControllers())
				.as("未扫描到任何 C 端 controller，测试路径可能已失效")
				.isNotEmpty();
	}

	@Test
	void cEndSignInAndRecordEndpointsAreExposedWithoutPermissions() throws IOException {
		Path signIn = projectRoot.resolve("aryn-user/aryn-user-biz/src/main/java/com/aryn/cloud/user/"
				+ "controller/app/AppSignInController.java");
		assertThat(signIn).exists();
		String content = Files.readString(signIn);

		// 签到页所需的三个接口都应落在 C 端控制器上
		assertThat(content).contains("\"/app/signin\"");
		assertThat(content).contains("\"/configs\"");
		assertThat(content).contains("\"/records\"");
		assertThat(content).doesNotContain("@SaCheckPermission");
	}

	private List<Path> appControllers() throws IOException {
		Path javaRoot = projectRoot.resolve("aryn-mall-java");
		if (!Files.isDirectory(javaRoot)) {
			javaRoot = projectRoot;
		}
		try (Stream<Path> stream = Files.walk(javaRoot)) {
			return stream
					.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith("Controller.java"))
					.filter(p -> p.toString().replace('\\', '/').contains("/controller/app/"))
					.filter(p -> !p.toString().contains("/target/"))
					.toList();
		}
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
