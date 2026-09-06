
package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.dto.DeliveryBindingDTO;
import com.aryn.cloud.order.api.dto.DeliveryOnboardDTO;
import com.aryn.cloud.order.api.dto.DeliveryStaffDTO;
import com.aryn.cloud.order.api.entity.DeliveryQualificationOperation;
import com.aryn.cloud.order.api.entity.DeliveryStaff;
import com.aryn.cloud.order.api.vo.DeliveryStaffManagerVO;
import com.aryn.cloud.order.api.vo.DeliveryStaffOnboardVO;
import com.aryn.cloud.order.api.vo.MallUserBindingVO;
import com.aryn.cloud.order.api.vo.SysUserForOnboardVO;
import com.aryn.cloud.order.service.IDeliveryQualificationOperationService;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 配送员管理
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/delivery/staff")
@Tag(description = "delivery-staff", name = "配送员管理")
public class DeliveryStaffController {

	private final IDeliveryStaffService deliveryStaffService;

	private final IDeliveryQualificationOperationService deliveryQualificationOperationService;

	@Operation(summary = "配送员详情")
	@SaCheckPermission("delivery:staff:get")
	@GetMapping("{id}")
	public Result<DeliveryStaff> getById(@PathVariable String id) {
		return Result.success(deliveryStaffService.getById(id));
	}

	@Operation(summary = "配送员列表")
	@SaCheckPermission("delivery:staff:list")
	@GetMapping("list")
	public Result<List<DeliveryStaff>> list() {
		return Result.success(deliveryStaffService.list());
	}

	@Operation(summary = "配送员分页列表")
	@SaCheckPermission("delivery:staff:page")
	@GetMapping("/page")
	public Result<IPage<DeliveryStaff>> page(Page page, DeliveryStaff deliveryStaff) {
		return Result.success(deliveryStaffService.page(page,
				com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(deliveryStaff)));
	}

	@Operation(summary = "配送员管理分页（含绑定与权限摘要）")
	@SaCheckPermission("delivery:staff:page")
	@GetMapping("/manager-page")
	public Result<Page<DeliveryStaffManagerVO>> managerPage(Page<DeliveryStaff> page,
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String bindingStatus,
			@RequestParam(required = false) String status) {
		return Result.success(deliveryStaffService.managerPage(page, keyword, bindingStatus, status));
	}

	@Operation(summary = "向导式创建配送员")
	@SysLog("向导式创建配送员")
	@SaCheckPermission("delivery:staff:add")
	@PostMapping("/onboard")
	public Result<DeliveryStaffOnboardVO> onboard(@RequestBody @Valid DeliveryOnboardDTO dto) {
		return Result.success(deliveryStaffService.onboard(dto, operatorName()));
	}

	@Operation(summary = "绑定商城账号")
	@SysLog("配送员绑定商城账号")
	@SaCheckPermission("delivery:staff:bind")
	@PutMapping("/{id}/binding")
	public Result<Boolean> bindMallUser(@PathVariable String id, @RequestBody @Valid DeliveryBindingDTO dto) {
		deliveryStaffService.bindMallUser(id, dto.getMallUserId(), operatorName());
		return Result.success(Boolean.TRUE);
	}

	@Operation(summary = "解绑商城账号")
	@SysLog("配送员解绑商城账号")
	@SaCheckPermission("delivery:staff:bind")
	@DeleteMapping("/{id}/binding")
	public Result<Boolean> unbindMallUser(@PathVariable String id) {
		deliveryStaffService.unbindMallUser(id, operatorName());
		return Result.success(Boolean.TRUE);
	}

	@Operation(summary = "修改接单状态")
	@SysLog("修改配送员接单状态")
	@SaCheckPermission("delivery:staff:availability")
	@PutMapping("/{id}/availability")
	public Result<Boolean> updateAvailability(@PathVariable String id, @RequestParam String status) {
		return Result.success(deliveryStaffService.updateStatus(id, status));
	}

	@Operation(summary = "开通或停用配送资格")
	@SysLog("修改配送员配送资格")
	@SaCheckPermission("delivery:staff:qualification")
	@PutMapping("/{id}/qualification")
	public Result<Boolean> updateQualification(@PathVariable String id, @RequestParam boolean enabled) {
		return Result.success(deliveryStaffService.changeQualification(id, enabled, operatorName()));
	}

	@Operation(summary = "搜索可绑定商城用户")
	@SaCheckPermission("delivery:staff:bind")
	@GetMapping("/binding-mall-user-search")
	public Result<List<MallUserBindingVO>> searchMallUsers(@RequestParam(required = false) String keyword) {
		return Result.success(deliveryStaffService.searchMallUsersForBinding(keyword, 20));
	}

	@Operation(summary = "搜索可选员工账号")
	@SaCheckPermission("delivery:staff:add")
	@GetMapping("/onboard-sys-user-search")
	public Result<List<SysUserForOnboardVO>> searchSysUsers(@RequestParam(required = false) String keyword) {
		return Result.success(deliveryStaffService.searchSysUsersForOnboard(keyword, 20));
	}

	@Operation(summary = "查询配送资格待处理操作（授权/回收补偿）")
	@SaCheckPermission("delivery:staff:qualification")
	@GetMapping("/qualification-operations")
	public Result<List<DeliveryQualificationOperation>> qualificationOperations(
			@RequestParam(required = false) String status) {
		return Result.success(deliveryQualificationOperationService
			.list(Wrappers.<DeliveryQualificationOperation>lambdaQuery()
				.eq(StrUtil.isNotBlank(status), DeliveryQualificationOperation::getStatus, status)
				.orderByDesc(DeliveryQualificationOperation::getCreateTime)
				.last("LIMIT 100")));
	}

	/**
	 * 旧版直接新增配送员入口：会绕过向导校验产生缺少绑定与资格的半成品资料。
	 * 已收口为向导式创建（POST /delivery/staff/onboard），本接口保留一个版本周期返回明确错误。
	 */
	@Deprecated
	@Operation(summary = "新增配送员（已废弃，请使用向导式创建）")
	@SysLog("新增配送员（已废弃入口）")
	@SaCheckPermission("delivery:staff:add")
	@PostMapping
	public Result<Boolean> save(@RequestBody @Valid DeliveryStaffDTO dto) {
		throw new ArynBusinessException("请使用配送员向导创建：新增配送员已统一走「新增配送员」向导（绑定商城账号、开通资格一次完成）");
	}

	@Operation(summary = "编辑配送员")
	@SysLog("编辑配送员")
	@SaCheckPermission("delivery:staff:edit")
	@PutMapping
	public Result<Boolean> update(@RequestBody @Valid DeliveryStaffDTO dto) {
		return Result.success(deliveryStaffService.updateStaffProfile(dto));
	}

	@Operation(summary = "删除配送员")
	@SysLog("删除配送员")
	@SaCheckPermission("delivery:staff:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable String id) {
		return Result.success(deliveryStaffService.deleteStaff(id, operatorName()));
	}

	@Operation(summary = "更新配送员状态")
	@SysLog("更新配送员状态")
	@SaCheckPermission("delivery:staff:status")
	@PutMapping("/{id}/status")
	public Result<Boolean> updateStatus(@PathVariable String id, @RequestParam String status) {
		return Result.success(deliveryStaffService.updateStatus(id, status));
	}

	private String operatorName() {
		return SecurityUtils.requireUser().getUsername();
	}

}