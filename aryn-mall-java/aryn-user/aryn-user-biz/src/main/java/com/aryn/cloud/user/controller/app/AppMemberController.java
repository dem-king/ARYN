package com.aryn.cloud.user.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.entity.MemberBenefit;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import com.aryn.cloud.user.service.IMemberBenefitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/app/member")
@Tag(name = "会员等级权益-API")
public class AppMemberController {

	private final IMemberBenefitService memberBenefitService;

	@GetMapping("/benefits")
	@Operation(summary = "获取当前会员有效权益")
	public Result<MemberBenefitsVO> benefits() {
		return Result.success(memberBenefitService.getUserBenefits(SecurityUtils.getUser().getUserId()));
	}

	@GetMapping("/levels")
	@Operation(summary = "获取启用的会员等级")
	public Result<List<MemberLevel>> levels() {
		return Result.success(memberBenefitService.getEnabledLevels());
	}

	@GetMapping("/level-benefits")
	@Operation(summary = "获取启用的等级权益")
	public Result<List<MemberBenefit>> levelBenefits(@RequestParam String levelId) {
		return Result.success(memberBenefitService.getEnabledLevelBenefits(levelId));
	}

}
