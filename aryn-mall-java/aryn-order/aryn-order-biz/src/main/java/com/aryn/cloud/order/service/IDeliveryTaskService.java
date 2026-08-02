
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.dto.DeliveryAssignDTO;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.vo.DeliveryProgressVO;

import java.util.List;

/**
 * 配送任务
 *
 * @author aryn
 * @since 2025/7/31
 */
public interface IDeliveryTaskService extends IService<DeliveryTask> {

	boolean createTaskOnPay(OrderInfo orderInfo, List<OrderItemEntity> orderItems);

	String assignTasks(DeliveryAssignDTO dto);

	boolean reassign(String taskId, String staffId);

	DeliveryTask getTaskDetail(String id);

	boolean arrive(String taskId, String staffId);

	boolean signOnReceive(String orderId);

	DeliveryProgressVO getProgress(String orderId);

	boolean cancel(String taskId);

	/**
	 * 按订单ID取消配送任务（退款完成时调用）
	 * @param orderId 订单ID
	 * @return 是否成功
	 */
	boolean cancelByOrderId(String orderId);

	/**
	 * 配送员上报异常
	 * @param taskId 任务ID
	 * @param staffId 配送员ID
	 * @param reasonCode 异常原因编码
	 * @param reasonDesc 异常说明
	 * @param materialIds 异常图片素材ID列表
	 * @return 是否成功
	 */
	boolean reportException(String taskId, String staffId, String reasonCode, String reasonDesc, List<String> materialIds);

	/**
	 * 管理员将任务置为待退回
	 * @param taskId 任务ID
	 * @return 是否成功
	 */
	boolean returnPending(String taskId);

	/**
	 * 管理员确认商品退回仓库
	 * @param taskId 任务ID
	 * @param remark 备注
	 * @return 是否成功
	 */
	boolean returnConfirm(String taskId, String remark);

	/**
	 * 管理员关闭异常任务
	 * @param taskId 任务ID
	 * @param reason 关闭原因
	 * @return 是否成功
	 */
	boolean close(String taskId, String reason);

	/**
	 * 送达并关联凭证图片
	 * @param taskId 任务ID
	 * @param staffId 配送员ID
	 * @param materialIds 凭证素材ID列表
	 * @param remark 备注
	 * @return 是否成功
	 */
	boolean arriveWithEvidence(String taskId, String staffId, List<String> materialIds, String remark);

	/**
	 * 查询任务的凭证列表
	 * @param taskId 任务ID
	 * @return 凭证列表
	 */
	List<com.aryn.cloud.order.api.entity.DeliveryEvidence> listEvidence(String taskId);

	/**
	 * 查询任务的操作日志
	 * @param taskId 任务ID
	 * @return 日志列表
	 */
	List<com.aryn.cloud.order.api.entity.DeliveryTaskLog> listLogs(String taskId);

}
