package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.GroupBuyRecord;
import com.aryn.cloud.promotion.api.vo.GroupBuyRecordVO;

public interface IGroupBuyRecordService extends IService<GroupBuyRecord> {

	GroupBuyRecord openGroup(String activityId, String userId);

	GroupBuyRecord joinGroup(String recordId, String userId);

	IPage<GroupBuyRecordVO> getPageByActivityId(Page page, String activityId, String userId);

	void handlePaySuccess(String orderId);

	void handleGroupExpire();

	void handleActivityExpire();
}
