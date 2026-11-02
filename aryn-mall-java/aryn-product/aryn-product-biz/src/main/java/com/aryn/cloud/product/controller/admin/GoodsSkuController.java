package com.aryn.cloud.product.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.product.api.vo.GoodsSkuDisplayVO;
import com.aryn.cloud.product.service.IGoodsSkuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品sku前端控制器
 *
 * @author aryn
 * @since 2026/10/31
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/goodssku")
@Tag(description = "goodssku", name = "商品sku")
public class GoodsSkuController {

	private final IGoodsSkuService goodsSkuService;

	@Operation(summary = "批量查询SKU展示信息（商品名/规格/售价，含已下架）")
	@SaCheckPermission("product:goodsspu:get")
	@GetMapping("/byIds/{ids}")
	public Result<List<GoodsSkuDisplayVO>> getDisplayByIds(@PathVariable List<String> ids) {
		return Result.success(goodsSkuService.getDisplayByIds(ids));
	}

}
