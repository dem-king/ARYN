package com.aryn.cloud.user.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.UserPointsInfoVO;
import com.aryn.cloud.user.mapper.MemberLevelMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C端积分信息
 *
 * @author 雨滴kian
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/points")
@Tag(name = "积分-API")
public class AppPointsController {

	private final UserInfoMapper userInfoMapper;

	private final MemberLevelMapper memberLevelMapper;

	@Operation(summary = "用户积分信息")
	@GetMapping("/info")
	public Result<UserPointsInfoVO> info() {
		String userId = SecurityUtils.getUser().getUserId();
		UserInfo userInfo = userInfoMapper.selectById(userId);

		UserPointsInfoVO vo = new UserPointsInfoVO();
		if (userInfo != null) {
			vo.setPoint(userInfo.getPoint());
			vo.setBalance(userInfo.getBalance());
			if (userInfo.getMemberLevelId() != null) {
				MemberLevel level = memberLevelMapper.selectById(userInfo.getMemberLevelId());
				if (level != null) {
					vo.setLevelName(level.getLevelName());
				}
			}
		}
		return Result.success(vo);
	}

}
