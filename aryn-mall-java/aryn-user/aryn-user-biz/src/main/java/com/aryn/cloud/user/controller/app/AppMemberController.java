package com.aryn.cloud.user.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.vo.AppMemberBenefitVO;
import com.aryn.cloud.user.api.vo.AppMemberLevelVO;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import com.aryn.cloud.user.api.vo.MemberCurrentInfoVO;
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
	public Result<List<AppMemberLevelVO>> levels() {
		// 只回展示字段：等级实体带 createBy/tenantId/delFlag 等内部字段
		return Result.success(memberBenefitService.getEnabledLevels().stream().map(source -> {
			AppMemberLevelVO vo = new AppMemberLevelVO();
			vo.setId(source.getId());
			vo.setLevelName(source.getLevelName());
			vo.setLevelIcon(source.getLevelIcon());
			vo.setConditionType(source.getConditionType());
			vo.setConditionValue(source.getConditionValue());
			vo.setSortOrder(source.getSortOrder());
			return vo;
		}).toList());
	}

	@GetMapping("/level-benefits")
	@Operation(summary = "获取启用的等级权益")
	public Result<List<AppMemberBenefitVO>> levelBenefits(@RequestParam String levelId) {
		// 只回展示字段：权益实体带 createBy/tenantId/delFlag 等内部字段
		return Result.success(memberBenefitService.getEnabledLevelBenefits(levelId).stream().map(source -> {
			AppMemberBenefitVO vo = new AppMemberBenefitVO();
			vo.setId(source.getId());
			vo.setBenefitName(source.getBenefitName());
			vo.setBenefitType(source.getBenefitType());
			vo.setBenefitValue(source.getBenefitValue());
			vo.setDescription(source.getDescription());
			return vo;
		}).toList());
	}

	@GetMapping("/current-info")
	@Operation(summary = "获取当前登录用户会员等级与标签（装修条件渲染用）")
	public Result<MemberCurrentInfoVO> currentInfo() {
		return Result.success(memberBenefitService.getCurrentMemberInfo(SecurityUtils.getUser().getUserId()));
	}

}
