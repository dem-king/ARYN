package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.GroupBuyMember;

import java.util.Collection;
import java.util.List;

public interface IGroupBuyMemberService extends IService<GroupBuyMember> {

	List<GroupBuyMember> getByRecordId(String recordId);

	/**
	 * 按拼团记录 ID 批量查询成员（一次 SQL），供列表页头像/昵称装配避免逐条查询。
	 *
	 * @param recordIds 拼团记录 ID 集合；为空时直接返回空列表
	 * @return 成员列表，按 createTime、id 升序稳定排列
	 */
	List<GroupBuyMember> getByRecordIds(Collection<String> recordIds);

	int countByActivityIdAndUserId(String activityId, String userId);
}
