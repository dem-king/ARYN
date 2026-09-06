package com.aryn.cloud.order.controller.admin;

import com.aryn.cloud.common.core.util.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryRegionControllerTest {

	@Test
	void treeLoadsBuiltinRegionData() {
		DeliveryRegionController controller = new DeliveryRegionController();

		Result<List<Map<String, Object>>> result = controller.tree();

		assertThat(result.getData()).isNotNull();
		// 民政部省级行政区划共 31 个
		assertThat(result.getData()).hasSize(31);

		Map<String, Object> beijing = findByName(result.getData(), "北京市");
		assertThat(beijing).isNotNull();
		assertThat(beijing.get("code")).isEqualTo("11");

		List<Map<String, Object>> cities = childrenOf(beijing);
		Map<String, Object> shixiaqu = findByName(cities, "市辖区");
		assertThat(shixiaqu).isNotNull();
		assertThat(shixiaqu.get("code")).isEqualTo("1101");

		List<Map<String, Object>> areas = childrenOf(shixiaqu);
		Map<String, Object> dongcheng = findByName(areas, "东城区");
		assertThat(dongcheng).isNotNull();
		assertThat(dongcheng.get("code")).isEqualTo("110101");
		assertThat(dongcheng.get("children")).isNull();
	}

	@Test
	void treeCoversAllProvinceLevels() {
		DeliveryRegionController controller = new DeliveryRegionController();
		List<Map<String, Object>> tree = controller.tree().getData();

		// 每个省级节点都存在市级子节点，市级节点都存在区县子节点
		for (Map<String, Object> province : tree) {
			List<Map<String, Object>> cities = childrenOf(province);
			assertThat(cities).as("%s 缺少市级数据", province.get("name")).isNotEmpty();
			for (Map<String, Object> city : cities) {
				assertThat(childrenOf(city)).as("%s/%s 缺少区县数据", province.get("name"), city.get("name"))
					.isNotEmpty();
			}
		}
	}

	private Map<String, Object> findByName(List<Map<String, Object>> nodes, String name) {
		return nodes.stream().filter(node -> name.equals(node.get("name"))).findFirst().orElse(null);
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> childrenOf(Map<String, Object> node) {
		return (List<Map<String, Object>>) node.getOrDefault("children", List.of());
	}

}
