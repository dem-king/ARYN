package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.PointsConfig;
import com.aryn.cloud.user.api.entity.PointsExclude;
import com.aryn.cloud.user.api.entity.PointsGlobalConfig;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.enums.PointsSceneEnum;
import com.aryn.cloud.user.api.vo.PointsDeductContextVO;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IPointsConfigService;
import com.aryn.cloud.user.service.IPointsExcludeService;
import com.aryn.cloud.user.service.IPointsGlobalConfigService;
import com.aryn.cloud.user.service.IPointsGrantService;
import com.aryn.cloud.user.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 积分统一发放/抵扣服务。
 *
 * <p>规则来源为 {@code points_config}：发放场景读取 rule_type=1，抵扣场景读取 rule_type=2。
 * 配置不存在时，带指定积分的发放（订单完成）回退到历史口径；固定积分场景不发放。
 * 配置存在但被禁用时一律不发放，保证运营可以随时关停。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsGrantServiceImpl implements IPointsGrantService {

	private final IPointsConfigService pointsConfigService;

	private final IPointsRecordService pointsRecordService;

	private final UserInfoMapper userInfoMapper;

	private final IPointsGlobalConfigService pointsGlobalConfigService;

	private final IPointsExcludeService pointsExcludeService;

	@Override
	public int grant(String userId, PointsSceneEnum scene, String bizNo, Integer overridePoints, String remark) {
		if (scene == null || !scene.isEarnScene()) {
			throw new ArynBusinessException("积分发放场景不合法");
		}
		validateBizNo(bizNo);
		List<PointsConfig> rules = listRules(scene, scene.getChangeType());
		Integer points = overridePoints;
		if (!rules.isEmpty()) {
			PointsConfig enabled = rules.stream()
				.filter(rule -> "0".equals(rule.getStatus()))
				.findFirst()
				.orElse(null);
			if (enabled == null) {
				return 0;
			}
			if (points == null) {
				points = enabled.getPointValue();
			}
		}
		else if (points == null) {
			return 0;
		}
		if (points == null || points <= 0) {
			return 0;
		}
		pointsRecordService.recordPointsChange(userId, scene.getChangeType(), points, scene.getCode(), remark, bizNo);
		return points;
	}

	@Override
	public int getOrderCompleteRate() {
		List<PointsConfig> rules = listRules(PointsSceneEnum.ORDER_COMPLETE, "1");
		if (rules.isEmpty()) {
			// 未执行配置种子脚本的存量库保持原有 1:1 发放口径
			return 1;
		}
		return rules.stream()
			.filter(rule -> "0".equals(rule.getStatus()))
			.map(PointsConfig::getPointValue)
			.filter(value -> value != null && value > 0)
			.findFirst()
			.orElse(0);
	}

	@Override
	public PointsDeductContextVO getDeductContext(String userId) {
		List<PointsConfig> rules = listRules(PointsSceneEnum.ORDER_DEDUCT, "2");
		PointsConfig enabled = rules.stream()
			.filter(rule -> "0".equals(rule.getStatus()))
			.findFirst()
			.orElse(null);
		UserInfo userInfo = userInfoMapper.selectById(userId);
		int availablePoints = userInfo == null || userInfo.getPoint() == null ? 0
				: Math.max(userInfo.getPoint(), 0);

		PointsDeductContextVO context = new PointsDeductContextVO();
		context.setAvailablePoints(availablePoints);
		if (enabled == null || enabled.getPointValue() == null || enabled.getPointValue() <= 0) {
			context.setEnabled(false);
			context.setPointsPerYuan(0);
			return context;
		}
		context.setEnabled(true);
		context.setPointsPerYuan(enabled.getPointValue());
		fillRiskControlConfig(context);
		return context;
	}

	/**
	 * 抵扣风控配置（比例/门槛/封顶）与品类黑名单一并随上下文下发，
	 * 订单域只做一次远程调用即可完成过滤，避免黑名单走第二条 RPC 链路。
	 */
	private void fillRiskControlConfig(PointsDeductContextVO context) {
		try {
			PointsGlobalConfig global = pointsGlobalConfigService.getEffectiveConfig();
			if (global != null) {
				context.setDeductMaxRatio(global.getDeductMaxRatio());
				context.setMinUseAmount(global.getMinUseAmount());
				context.setMaxDeductAmount(global.getMaxDeductAmount());
			}
			List<PointsExclude> excludes = pointsExcludeService.listAll();
			if (excludes != null && !excludes.isEmpty()) {
				context.setExcludeCategoryIds(excludes.stream()
					.filter(rule -> PointsExclude.TYPE_CATEGORY.equals(rule.getTargetType()))
					.map(PointsExclude::getTargetId)
					.filter(Objects::nonNull)
					.toList());
				context.setExcludeSpuIds(excludes.stream()
					.filter(rule -> PointsExclude.TYPE_GOODS.equals(rule.getTargetType()))
					.map(PointsExclude::getTargetId)
					.filter(Objects::nonNull)
					.toList());
			}
		}
		catch (Exception e) {
			// 风控配置读取失败按缺省（20%/无门槛/不封顶/无黑名单）继续，不阻断下单
			log.error("读取积分风控配置失败，按默认规则继续", e);
		}
	}

	@Override
	public void deduct(String userId, Integer points, String bizNo, String remark) {
		validatePoints(points);
		validateBizNo(bizNo);
		pointsRecordService.recordPointsChange(userId, "2", points, PointsSceneEnum.ORDER_DEDUCT.getCode(), remark,
				bizNo);
	}

	@Override
	public void refund(String userId, Integer points, String bizNo, String remark) {
		validatePoints(points);
		validateBizNo(bizNo);
		pointsRecordService.recordPointsChange(userId, "1", points, PointsSceneEnum.ORDER_REFUND.getCode(), remark,
				bizNo);
	}

	private List<PointsConfig> listRules(PointsSceneEnum scene, String ruleType) {
		List<PointsConfig> rules = pointsConfigService.list(Wrappers.<PointsConfig>lambdaQuery()
			.eq(PointsConfig::getTriggerScene, scene.getCode())
			.eq(PointsConfig::getRuleType, ruleType)
			// 历史数据可能同一场景存在多条启用规则（旧管理端可自由建），固定按更新时间倒序取最新，
			// 避免同一次结算/发放因查询顺序不同取到不同规则
			.orderByDesc(PointsConfig::getUpdateTime)
			.orderByDesc(PointsConfig::getCreateTime));
		if (rules.stream().filter(rule -> "0".equals(rule.getStatus())).count() > 1) {
			log.error("积分规则存在同场景多条启用配置，scene={}，请到积分配置收敛", scene.getCode());
		}
		return rules;
	}

	private void validatePoints(Integer points) {
		if (points == null || points <= 0) {
			throw new ArynBusinessException("积分变动值必须大于0");
		}
	}

	private void validateBizNo(String bizNo) {
		if (StringUtils.hasText(bizNo) && bizNo.length() > 64) {
			throw new ArynBusinessException("积分业务单号长度不能超过64");
		}
	}

}
