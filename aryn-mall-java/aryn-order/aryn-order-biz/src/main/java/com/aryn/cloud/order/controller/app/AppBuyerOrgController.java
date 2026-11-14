package com.aryn.cloud.order.controller.app;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.dto.BuyerOrgApplyDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgInviteCodeDTO;
import com.aryn.cloud.order.api.entity.BuyerOrgApply;
import com.aryn.cloud.order.api.entity.BuyerOrgInviteCode;
import com.aryn.cloud.order.api.vo.BuyerOrgApplyVO;
import com.aryn.cloud.order.api.vo.BuyerOrgMemberVO;
import com.aryn.cloud.order.api.vo.BuyerOrgVO;
import com.aryn.cloud.order.service.IBuyerOrgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采购主体 C 端接口。
 *
 * <p>岸上企业车创建前先取「我的工厂」；没有主体时走创建/搜索加入/邀请码三条路径。
 * 待审核主体允许建车下单（不享首单），审核通过后由 P1 引擎发首单权益。
 *
 * @author aryn
 * @since 2026/11/12
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/buyer-org")
@Tag(description = "buyer-org-app", name = "采购主体")
public class AppBuyerOrgController {

	private final IBuyerOrgService buyerOrgService;

	@Operation(summary = "我的采购主体")
	@GetMapping("/my")
	public Result<List<BuyerOrgVO>> my() {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(buyerOrgService.listMyOrgs(ArynTenantContextHolder.getTenantId(),
			user.getUserId()));
	}

	@Operation(summary = "搜索采购主体（名称/信用代码）")
	@GetMapping("/search")
	public Result<List<BuyerOrgVO>> search(@RequestParam(required = false) String keyword) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(buyerOrgService.search(ArynTenantContextHolder.getTenantId(),
			user.getUserId(), keyword));
	}

	@Operation(summary = "提交创建/加入申请")
	@PostMapping("/apply")
	public Result<BuyerOrgApply> apply(@Valid @RequestBody BuyerOrgApplyDTO dto) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(buyerOrgService.apply(ArynTenantContextHolder.getTenantId(),
			user.getUserId(), dto));
	}

	@Operation(summary = "我的申请单")
	@GetMapping("/apply/my")
	public Result<List<BuyerOrgApplyVO>> myApplies() {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(buyerOrgService.listMyApplies(ArynTenantContextHolder.getTenantId(),
			user.getUserId()));
	}

	@Operation(summary = "采购主体详情")
	@GetMapping("/{id}")
	public Result<BuyerOrgVO> detail(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(buyerOrgService.getDetail(ArynTenantContextHolder.getTenantId(),
			user.getUserId(), id));
	}

	@Operation(summary = "凭邀请码加入采购主体")
	@PostMapping("/join")
	public Result<BuyerOrgMemberVO> join(@RequestParam String code) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(buyerOrgService.joinByInviteCode(ArynTenantContextHolder.getTenantId(),
			user.getUserId(), code));
	}

	@Operation(summary = "采购主体成员列表")
	@GetMapping("/{id}/members")
	public Result<List<BuyerOrgMemberVO>> members(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(buyerOrgService.listMembers(ArynTenantContextHolder.getTenantId(),
			user.getUserId(), id));
	}

	@Operation(summary = "生成采购主体邀请码")
	@PostMapping("/{id}/invite-code")
	public Result<BuyerOrgInviteCode> inviteCode(@PathVariable String id,
			@RequestBody(required = false) BuyerOrgInviteCodeDTO dto) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(buyerOrgService.createInviteCode(ArynTenantContextHolder.getTenantId(),
			user.getUserId(), id, dto));
	}

	@Operation(summary = "退出采购主体")
	@PostMapping("/{id}/leave")
	public Result<Boolean> leave(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		buyerOrgService.leave(ArynTenantContextHolder.getTenantId(), user.getUserId(), id);
		return Result.success(Boolean.TRUE);
	}

	@Operation(summary = "厂长移除成员")
	@DeleteMapping("/{id}/members/{userId}")
	public Result<Boolean> removeMember(@PathVariable String id, @PathVariable String userId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		buyerOrgService.removeMember(ArynTenantContextHolder.getTenantId(), user.getUserId(), id, userId);
		return Result.success(Boolean.TRUE);
	}

}
