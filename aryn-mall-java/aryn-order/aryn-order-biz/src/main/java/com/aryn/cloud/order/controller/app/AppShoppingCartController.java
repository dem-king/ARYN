
package com.aryn.cloud.order.controller.app;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.dto.ShoppingCartCreateDTO;
import com.aryn.cloud.order.api.dto.ShoppingCartUpdateDTO;
import com.aryn.cloud.order.api.entity.ShoppingCart;
import com.aryn.cloud.order.service.IShoppingCartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车
 *
 * @author 雨滴kian
 * @since 2022/3/17 14:44
 */
@Slf4j
@AllArgsConstructor
@RestController
@Validated
@RequestMapping("/app/shopping-cart")
@Tag(description = "shoppingcart", name = "购物车-API")
public class AppShoppingCartController {

	private final IShoppingCartService shoppingCartService;

	@Operation(summary = "购物车列表")
	@GetMapping("/page")
	public Result page(Page page, ShoppingCart shoppingCart) {
		shoppingCart.setUserId(SecurityUtils.getUser().getUserId());
		return Result.success(shoppingCartService.apiPage(page, shoppingCart));
	}

	@Operation(summary = "购物车添加")
	@PostMapping
	public Result<Boolean> add(@Valid @RequestBody ShoppingCartCreateDTO request) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(shoppingCartService.saveShoppingCart(userId, request));
	}

	@Operation(summary = "购物车修改")
	@PutMapping
	public Result<Boolean> edit(@Valid @RequestBody ShoppingCartUpdateDTO request) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(shoppingCartService.updateShoppingCart(userId, request));
	}

	@Operation(summary = "购物车删除")
	@PostMapping("/del")
	public Result<Boolean> del(@RequestBody @NotEmpty List<@NotBlank String> ids) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(shoppingCartService.removeByUserId(userId, ids));
	}

	@Operation(summary = "查询购物车数量")
	@GetMapping("/count")
	public Result count() {
		String userId = SecurityUtils.getUser().getUserId();
		if (StrUtil.isEmpty(userId)) {
			return Result.success(0);
		}
		return Result.success(
				shoppingCartService.count(Wrappers.<ShoppingCart>lambdaQuery().eq(ShoppingCart::getUserId, userId)));
	}

}
