package com.aryn.cloud.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.entity.MemberBenefitLevelRel;
import com.aryn.cloud.user.api.entity.MemberLevelRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.MemberLevelRecordVO;
import com.aryn.cloud.user.mapper.MemberLevelMapper;
import com.aryn.cloud.user.mapper.MemberBenefitLevelRelMapper;
import com.aryn.cloud.user.mapper.MemberLevelRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberLevelService;
import com.aryn.cloud.user.service.IMemberBenefitService;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.user.api.entity.MemberBenefit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 会员等级配置
 *
 * @author 雨滴kian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberLevelServiceImpl extends ServiceImpl<MemberLevelMapper, MemberLevel>
		implements IMemberLevelService {

	private final MemberLevelRecordMapper memberLevelRecordMapper;

	private final UserInfoMapper userInfoMapper;

	private final MemberBenefitLevelRelMapper memberBenefitLevelRelMapper;

	private final IMemberBenefitService memberBenefitService;

	@DubboReference
	private final RemoteCouponUserService remoteCouponUserService;

	@Override
	public IPage<MemberLevel> getPage(Page page, MemberLevel memberLevel) {
		return this.page(page, Wrappers.<MemberLevel>lambdaQuery()
				.like(memberLevel != null && StrUtil.isNotBlank(memberLevel.getLevelName()),
						MemberLevel::getLevelName, memberLevel == null ? null : memberLevel.getLevelName())
				.eq(memberLevel != null && StrUtil.isNotBlank(memberLevel.getConditionType()),
						MemberLevel::getConditionType, memberLevel == null ? null : memberLevel.getConditionType())
				.eq(memberLevel != null && StrUtil.isNotBlank(memberLevel.getStatus()),
						MemberLevel::getStatus, memberLevel == null ? null : memberLevel.getStatus())
				.orderByAsc(MemberLevel::getSortOrder).orderByDesc(MemberLevel::getCreateTime));
	}

	@Override
	public MemberLevel getDetailById(String id) {
		return this.getById(id);
	}

	@Override
	public boolean saveLevel(MemberLevel memberLevel) {
		validateLevel(memberLevel);
		clearSystemFields(memberLevel);
		// 校验升级条件值是否重复
		long count = this.count(Wrappers.<MemberLevel>lambdaQuery()
				.eq(MemberLevel::getConditionType, memberLevel.getConditionType())
				.eq(MemberLevel::getConditionValue, memberLevel.getConditionValue()));
		if (count > 0) {
			throw new ArynBusinessException("相同升级条件类型的条件值已存在");
		}
		return this.save(memberLevel);
	}

	@Override
	public boolean updateLevel(MemberLevel memberLevel) {
		validateLevel(memberLevel);
		clearSystemFields(memberLevel);
		// 校验升级条件值是否重复（排除自身）
		long count = this.count(Wrappers.<MemberLevel>lambdaQuery()
				.eq(MemberLevel::getConditionType, memberLevel.getConditionType())
				.eq(MemberLevel::getConditionValue, memberLevel.getConditionValue())
				.ne(MemberLevel::getId, memberLevel.getId()));
		if (count > 0) {
			throw new ArynBusinessException("相同升级条件类型的条件值已存在");
		}
		return this.updateById(memberLevel);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean deleteLevel(String id) {
		// 校验该等级下是否存在会员
		long userCount = userInfoMapper
				.selectCount(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getMemberLevelId, id));
		if (userCount > 0) {
			throw new ArynBusinessException("该等级下存在会员，不允许删除");
		}
		memberBenefitLevelRelMapper.delete(
				Wrappers.<MemberBenefitLevelRel>lambdaQuery().eq(MemberBenefitLevelRel::getLevelId, id));
		return this.removeById(id);
	}

	@Override
	public IPage<MemberLevelRecordVO> getRecordPage(Page page, String userId) {
		return memberLevelRecordMapper.selectRecordPage(page, userId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void recalculateLevel(String userId) {
		UserInfo userInfo = userInfoMapper.selectById(userId);
		if (userInfo == null) {
			return;
		}

		// 排序号越大代表等级越高，不同条件类型之间不得比较阈值大小。
		List<MemberLevel> levels = this.list(Wrappers.<MemberLevel>lambdaQuery()
				.eq(MemberLevel::getStatus, "0")
				.orderByAsc(MemberLevel::getSortOrder));

		if (levels.isEmpty()) {
			return;
		}

		// 根据等级的升级条件类型匹配，取满足条件的最高等级
		MemberLevel matchedLevel = null;
		for (MemberLevel level : levels) {
			// 读取链路只做降级处理：单条脏配置不能拖垮全体会员的等级重算
			if (!isUsableCondition(level)) {
				log.warn("会员等级配置不完整，跳过参与重算：levelId={}, conditionType={}, conditionValue={}",
						level.getId(), level.getConditionType(), level.getConditionValue());
				continue;
			}
			BigDecimal compareValue;
			if ("1".equals(level.getConditionType())) {
				compareValue = userInfo.getTotalConsume() == null ? BigDecimal.ZERO : userInfo.getTotalConsume();
			}
			else {
				compareValue = BigDecimal.valueOf(userInfo.getTotalPoint() == null ? 0 : userInfo.getTotalPoint());
			}
			if (compareValue.compareTo(level.getConditionValue()) >= 0
					&& (matchedLevel == null || level.getSortOrder() > matchedLevel.getSortOrder())) {
				matchedLevel = level;
			}
		}

		// 判断等级是否变化
		String oldLevelId = userInfo.getMemberLevelId();
		String newLevelId = matchedLevel != null ? matchedLevel.getId() : null;

		if ((oldLevelId == null && newLevelId != null) || (oldLevelId != null && !oldLevelId.equals(newLevelId))) {
			if (userInfoMapper.updateMemberLevel(userId, newLevelId) == 0) {
				throw new ArynBusinessException("会员等级更新失败");
			}

			// 记录等级变更
			MemberLevelRecord record = new MemberLevelRecord();
			record.setUserId(userId);
			record.setOldLevelId(oldLevelId);
			record.setNewLevelId(newLevelId);
			record.setChangeReason("系统自动计算");
			memberLevelRecordMapper.insert(record);

			if (newLevelId != null) {
					for (MemberBenefit benefit : memberBenefitService.getLevelBenefits(newLevelId)) {
						if ("3".equals(benefit.getBenefitType())) {
							remoteCouponUserService.grantMemberBenefitCoupon(
									benefit.getBenefitValue(), userId, newLevelId + ":" + benefit.getId());
						}
				}
			}
		}
	}

	private void validateLevel(MemberLevel memberLevel) {
		if (memberLevel == null || memberLevel.getLevelName() == null || memberLevel.getLevelName().isBlank()) {
			throw new ArynBusinessException("会员等级名称不能为空");
		}
		if (memberLevel.getStatus() != null && !"0".equals(memberLevel.getStatus())
				&& !"1".equals(memberLevel.getStatus())) {
			throw new ArynBusinessException("会员等级状态不合法");
		}
		if (!"1".equals(memberLevel.getConditionType())
				&& !"2".equals(memberLevel.getConditionType())) {
			throw new ArynBusinessException("升级条件类型不合法");
		}
		if (memberLevel.getConditionValue() == null
				|| memberLevel.getConditionValue().compareTo(BigDecimal.ZERO) < 0) {
			throw new ArynBusinessException("升级条件值不能小于0");
		}
		if (memberLevel.getSortOrder() == null || memberLevel.getSortOrder() < 0) {
			throw new ArynBusinessException("等级排序号不能小于0");
		}
	}

	/**
	 * 判断等级配置是否可用于成长值匹配（读取链路的降级判定，不抛异常）
	 */
	private boolean isUsableCondition(MemberLevel memberLevel) {
		return memberLevel != null
				&& ("1".equals(memberLevel.getConditionType()) || "2".equals(memberLevel.getConditionType()))
				&& memberLevel.getConditionValue() != null
				&& memberLevel.getConditionValue().compareTo(BigDecimal.ZERO) >= 0
				&& memberLevel.getSortOrder() != null;
	}

	private void clearSystemFields(MemberLevel memberLevel) {
		memberLevel.setTenantId(null);
		memberLevel.setCreateBy(null);
		memberLevel.setUpdateBy(null);
		memberLevel.setCreateTime(null);
		memberLevel.setUpdateTime(null);
		memberLevel.setDelFlag(null);
	}

}
