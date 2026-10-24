package com.aryn.cloud.user.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.entity.PointsExchangeRecord;
import com.aryn.cloud.user.api.vo.AppPointsRecordVO;
import com.aryn.cloud.user.api.vo.PointsExchangeVO;
import com.aryn.cloud.user.api.vo.UserPointsInfoVO;
import com.aryn.cloud.user.mapper.MemberLevelMapper;
import com.aryn.cloud.user.mapper.PointsRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IPointsExchangeService;
import com.aryn.cloud.user.service.IPointsRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

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

	private final PointsRecordMapper pointsRecordMapper;

	private final IPointsRecordService pointsRecordService;

	private final IPointsExchangeService pointsExchangeService;

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
		fillExpiringSummary(vo, userId);
		return Result.success(vo);
	}

	/**
	 * 30 天内到期积分汇总：促活口径（「再不来就过期」），配置永不过期时恒为空。
	 */
	private void fillExpiringSummary(UserPointsInfoVO vo, String userId) {
		try {
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime until = now.plusDays(30);
			Integer expiring = pointsRecordMapper.sumRemainPointsBetween(userId, now, until);
			if (expiring != null && expiring > 0) {
				vo.setExpiringPoints(expiring);
				vo.setNearestExpireTime(pointsRecordMapper.selectNearestExpireTime(userId, now, until));
			}
		}
		catch (Exception e) {
			log.warn("查询即将过期积分汇总失败 userId={}", userId, e);
		}
	}

	@Operation(summary = "我的积分记录")
	@GetMapping("/records")
	public Result<IPage<AppPointsRecordVO>> records(Page page) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(pointsRecordService.getUserPage(page, userId).convert(AppPointsRecordVO::from));
	}

	@Operation(summary = "积分兑换列表（券模板信息 + 本人已兑次数）")
	@GetMapping("/exchanges")
	public Result<List<PointsExchangeVO>> exchanges() {
		return Result.success(pointsExchangeService.listApplicable(SecurityUtils.getUser().getUserId()));
	}

	@Operation(summary = "积分兑换优惠券")
	@PostMapping("/exchanges/{exchangeId}/redeem")
	public Result<PointsExchangeRecord> redeem(@PathVariable("exchangeId") String exchangeId) {
		return Result.success(
				pointsExchangeService.exchange(SecurityUtils.getUser().getUserId(), exchangeId));
	}

}
