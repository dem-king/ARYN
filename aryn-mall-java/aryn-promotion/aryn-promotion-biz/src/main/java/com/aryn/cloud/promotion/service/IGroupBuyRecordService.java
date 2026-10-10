package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.GroupBuyRecord;
import com.aryn.cloud.promotion.api.vo.GroupBuyOrderContextVO;
import com.aryn.cloud.promotion.api.vo.GroupBuyRecordVO;

public interface IGroupBuyRecordService extends IService<GroupBuyRecord> {

	GroupBuyRecord openGroup(String activityId, String userId);

	GroupBuyRecord joinGroup(String recordId, String userId);

	IPage<GroupBuyRecordVO> getPageByActivityId(Page page, String activityId, String userId);

	/**
	 * 拼团下单校验并取拼团价上下文；校验失败抛业务异常
	 */
	GroupBuyOrderContextVO getOrderContext(String recordId, String userId);

	/**
	 * 订单创建成功后绑定订单到成员；并发冲突/成员不可用时返回 false
	 */
	boolean bindOrder(String recordId, String userId, String orderId);

	/**
	 * 订单取消时释放成员占坑（订单关联清空、回待付款），幂等
	 */
	boolean releaseOrder(String orderId);

	void handlePaySuccess(String orderId);

	void handleGroupExpire();

	void handleActivityExpire();
}
