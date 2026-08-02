
package com.aryn.cloud.order.controller.admin;

import com.aryn.cloud.common.core.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 配送省市区树
 *
 * @author aryn
 * @since 2025/8/1
 */
@Slf4j
@RestController
@RequestMapping("/delivery/region")
@Tag(description = "delivery-region", name = "配送省市区树")
public class DeliveryRegionController {

	@Operation(summary = "省市区树")
	@GetMapping("/tree")
	public Result<List<Object>> tree() {
		return Result.success(Collections.emptyList());
	}

}