package com.aryn.cloud.boot.transaction;

import com.aryn.cloud.order.listener.ArynRefundListener;
import com.aryn.cloud.order.service.impl.OrderInfoServiceImpl;
import org.apache.dubbo.rpc.Filter;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * cloud 模式积分闭环的事务安全契约。
 *
 * <p>背景：seata-all 2.5 只内置 Alibaba Dubbo 的过滤器，没有注册 Apache Dubbo 3 的
 * {@code org.apache.dubbo.rpc.Filter} SPI，{@code @GlobalTransactional} 跨 Dubbo 调用时 XID
 * 不传播，下游（用户域积分库）写操作不会被注册为 Seata 分支，出现「订单回滚但积分已扣/已退」
 * 的脏数据。这里用静态契约守住三件事：
 * <ol>
 *   <li>跨 Dubbo 的 Seata XID 透传过滤器已注册；</li>
 *   <li>用户域（积分写操作所在服务）声明了 seata 依赖，cloud 下才会注册为 RM；</li>
 *   <li>下单、取消、退款回调三条会改积分的链路都在全局事务内。</li>
 * </ol>
 */
class CloudPointsTransactionContractTest {

	private static final String XID_FILTER_DESCRIPTOR = "META-INF/dubbo/org.apache.dubbo.rpc.Filter";

	private final Path projectRoot = findProjectRoot();

	@Test
	void dubboSeataXidFilterIsRegistered() throws Exception {
		List<URL> descriptors = Collections.list(getClass().getClassLoader().getResources(XID_FILTER_DESCRIPTOR));
		String merged = descriptors.stream().map(CloudPointsTransactionContractTest::readQuietly).reduce("",
				String::concat);
		assertThat(merged).as("Seata XID 未通过 Dubbo SPI 注册，cloud 跨服务事务会退化为非原子")
			.contains("seataXidDubboFilter=com.aryn.cloud.common.seata.dubbo.SeataXidDubboFilter");
		Class<?> filter = Class.forName("com.aryn.cloud.common.seata.dubbo.SeataXidDubboFilter");
		assertThat(Filter.class.isAssignableFrom(filter)).isTrue();
		assertThat(RootContext.KEY_XID).isNotBlank();
	}

	@Test
	void transactionParticipantsDeclareSeataDependency() throws IOException {
		// 下单全局事务的写参与方：用户域（积分扣减/返还）、营销域（锁券/营销预占/取消释放）、
		// 商品域（库存）与订单域。任一域未接入 Seata RM，回滚时都会留下单边数据。
		for (String module : List.of("aryn-user/aryn-user-biz", "aryn-promotion/aryn-promotion-biz",
				"aryn-product/aryn-product-biz", "aryn-order/aryn-order-biz")) {
			String pom = Files.readString(projectRoot.resolve(module + "/pom.xml"));
			assertThat(pom).as("%s 未声明 aryn-common-seata，cloud 下无法作为全局事务分支回滚", module)
				.contains("<artifactId>aryn-common-seata</artifactId>");
		}
	}

	@Test
	void pointsMutatingFlowsRunInGlobalTransaction() throws Exception {
		Method createOrder = OrderInfoServiceImpl.class.getMethod("createOrder",
				com.aryn.cloud.order.api.dto.CreateOrderDTO.class);
		assertThat(createOrder.isAnnotationPresent(GlobalTransactional.class)).as("下单扣积分需要全局事务").isTrue();

		Method cancelOrder = OrderInfoServiceImpl.class.getMethod("cancelOrder",
				com.aryn.cloud.order.api.entity.OrderInfo.class);
		assertThat(cancelOrder.isAnnotationPresent(GlobalTransactional.class)).as("取消返还积分需要全局事务").isTrue();

		Method onMessage = ArynRefundListener.class.getMethod("onMessage", String.class);
		assertThat(onMessage.isAnnotationPresent(GlobalTransactional.class))
			.as("整单退款返还积分（MQ 回调）需要全局事务")
			.isTrue();
	}

	private static String readQuietly(URL url) {
		try {
			return new String(url.openStream().readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new IllegalStateException("读取 Dubbo SPI 描述文件失败: " + url, exception);
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
