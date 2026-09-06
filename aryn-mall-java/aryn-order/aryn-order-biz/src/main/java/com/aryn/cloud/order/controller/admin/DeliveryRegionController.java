
package com.aryn.cloud.order.controller.admin;

import com.aryn.cloud.common.core.util.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 配送省市区树
 *
 * <p>行政区划数据来源于民政部 GB/T 2260 省市区编码（china-division 数据集），
 * 以静态资源 region/pca-code.json 内置，启动时一次性加载；区划调整时替换资源文件即可。
 *
 * @author aryn
 * @since 2025/8/1
 */
@Slf4j
@RestController
@RequestMapping("/delivery/region")
@Tag(description = "delivery-region", name = "配送省市区树")
public class DeliveryRegionController {

	private static final String REGION_DATA_RESOURCE = "/region/pca-code.json";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/**
	 * 省市区树缓存，节点结构 { code, name, children }
	 */
	private final List<Map<String, Object>> regionTree;

	public DeliveryRegionController() {
		this.regionTree = loadRegionTree();
	}

	@Operation(summary = "省市区树")
	@GetMapping("/tree")
	public Result<List<Map<String, Object>>> tree() {
		return Result.success(regionTree);
	}

	private List<Map<String, Object>> loadRegionTree() {
		try (InputStream in = DeliveryRegionController.class.getResourceAsStream(REGION_DATA_RESOURCE)) {
			if (in == null) {
				log.error("行政区划数据文件缺失: {}", REGION_DATA_RESOURCE);
				return Collections.emptyList();
			}
			return OBJECT_MAPPER.readValue(in, new TypeReference<>() {
			});
		} catch (IOException e) {
			log.error("加载行政区划数据失败: {}", REGION_DATA_RESOURCE, e);
			return Collections.emptyList();
		}
	}

}
