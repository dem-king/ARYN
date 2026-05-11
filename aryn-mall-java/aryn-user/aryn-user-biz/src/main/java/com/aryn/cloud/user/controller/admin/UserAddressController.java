
package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.user.api.entity.UserAddress;
import com.aryn.cloud.user.service.IUserAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户收货地址
 *
 * @author 雨滴kian
 * @since 2022/2/23 13:11
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/useraddress")
@Tag(description = "useraddress", name = "用户收货地址")
public class UserAddressController {

	private final IUserAddressService userAddressService;

	@Operation(summary = "收货地址分页列表")
	@SaCheckPermission("mall:useraddress:page")
	@GetMapping("/page")
	public Result<IPage<UserAddress>> page(Page page, UserAddress userAddress) {
		IPage<UserAddress> iPage = userAddressService.page(page, Wrappers.query(userAddress));
		return Result.success(iPage);
	}

}
