package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.GroupBuyMember;

import java.util.List;

public interface IGroupBuyMemberService extends IService<GroupBuyMember> {

	List<GroupBuyMember> getByRecordId(String recordId);

	int countByActivityIdAndUserId(String activityId, String userId);
}
