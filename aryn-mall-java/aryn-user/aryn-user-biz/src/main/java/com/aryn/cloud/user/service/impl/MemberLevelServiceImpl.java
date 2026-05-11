package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.entity.MemberLevelRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.MemberLevelRecordVO;
import com.aryn.cloud.user.mapper.MemberLevelMapper;
import com.aryn.cloud.user.mapper.MemberLevelRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberLevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

	@Override
	public IPage<MemberLevel> getPage(Page page, MemberLevel memberLevel) {
		return this.page(page,
				Wrappers.<MemberLevel>lambdaQuery().orderByAsc(MemberLevel::getSortOrder).orderByDesc(MemberLevel::getCreateTime));
	}

	@Override
	public MemberLevel getDetailById(String id) {
		return this.getById(id);
	}

	@Override
	public boolean saveLevel(MemberLevel memberLevel) {
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

		// 查询所有启用的等级配置，按升级条件值升序
		List<MemberLevel> levels = this.list(Wrappers.<MemberLevel>lambdaQuery()
				.eq(MemberLevel::getStatus, "0")
				.orderByAsc(MemberLevel::getConditionValue));

		if (levels.isEmpty()) {
			return;
		}

		// 根据等级的升级条件类型匹配，取满足条件的最高等级
		MemberLevel matchedLevel = null;
		for (MemberLevel level : levels) {
			BigDecimal compareValue;
			if ("1".equals(level.getConditionType())) {
				// 按累计消费金额
				compareValue = userInfo.getTotalConsume();
			}
			else {
				// 按累计积分
				compareValue = new BigDecimal(userInfo.getPoint());
			}
			if (compareValue.compareTo(level.getConditionValue()) >= 0) {
				matchedLevel = level;
			}
		}

		// 判断等级是否变化
		String oldLevelId = userInfo.getMemberLevelId();
		String newLevelId = matchedLevel != null ? matchedLevel.getId() : null;

		if ((oldLevelId == null && newLevelId != null) || (oldLevelId != null && !oldLevelId.equals(newLevelId))) {
			// 更新用户等级
			userInfo.setMemberLevelId(newLevelId);
			userInfoMapper.updateById(userInfo);

			// 记录等级变更
			MemberLevelRecord record = new MemberLevelRecord();
			record.setUserId(userId);
			record.setOldLevelId(oldLevelId);
			record.setNewLevelId(newLevelId);
			record.setChangeReason("系统自动计算");
			memberLevelRecordMapper.insert(record);
		}
	}

}
