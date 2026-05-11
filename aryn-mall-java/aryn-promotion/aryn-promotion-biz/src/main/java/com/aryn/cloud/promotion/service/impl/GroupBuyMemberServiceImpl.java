package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.GroupBuyMember;
import com.aryn.cloud.promotion.mapper.GroupBuyMemberMapper;
import com.aryn.cloud.promotion.service.IGroupBuyMemberService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class GroupBuyMemberServiceImpl extends ServiceImpl<GroupBuyMemberMapper, GroupBuyMember>
		implements IGroupBuyMemberService {

	@Override
	public List<GroupBuyMember> getByRecordId(String recordId) {
		return list(Wrappers.<GroupBuyMember>lambdaQuery()
				.eq(GroupBuyMember::getRecordId, recordId)
				.orderByDesc(GroupBuyMember::getCreateTime));
	}

	@Override
	public int countByActivityIdAndUserId(String activityId, String userId) {
		return Math.toIntExact(count(Wrappers.<GroupBuyMember>lambdaQuery()
				.eq(GroupBuyMember::getActivityId, activityId)
				.eq(GroupBuyMember::getUserId, userId)));
	}
}
