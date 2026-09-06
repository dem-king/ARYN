package com.aryn.cloud.order.service;

import com.aryn.cloud.order.api.entity.DeliveryQualificationOperation;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 配送资格操作（可靠授权/回收记录）
 *
 * <p>本地事务内创建待处理操作，事务提交后执行远程角色变更；
 * 失败按指数退避重试直到成功，保证不残留孤儿角色或未回收权限。
 *
 * @author aryn
 * @since 2026/9/6
 */
public interface IDeliveryQualificationOperationService extends IService<DeliveryQualificationOperation> {

	/**
	 * 创建或复用一条待处理资格操作（同租户内幂等键唯一，反向未完成操作会被关闭）
	 * @param staffId 配送员资料ID
	 * @param sysUserId 员工账号ID
	 * @param roleCode 角色编码
	 * @param operation GRANT授予 / REVOKE回收
	 * @param operator 操作人
	 * @return 待处理操作记录
	 */
	DeliveryQualificationOperation createPending(String staffId, String sysUserId, String roleCode, String operation,
			String operator);

	/**
	 * 执行一条待处理操作：条件认领 -> 远程角色变更（幂等） -> 标记完成
	 * @param operationId 操作记录ID
	 * @return true 已完成；false 执行失败或由其他实例处理，等待重试
	 */
	boolean processPending(String operationId);

	/**
	 * 查询已到期待处理的资格操作（供定时重试任务消费）
	 * @param limit 最大返回数量
	 * @return 待处理操作列表
	 */
	List<DeliveryQualificationOperation> listDueForRetry(int limit);

}
