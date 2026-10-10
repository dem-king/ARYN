
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.dto.DeliveryAssignDTO;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.vo.DeliveryCandidateOrderVO;
import com.aryn.cloud.order.api.vo.DeliveryProgressVO;

import java.util.List;
import java.util.Map;

/**
 * 配送任务
 *
 * @author aryn
 * @since 2025/7/31
 */
public interface IDeliveryTaskService extends IService<DeliveryTask> {

	boolean createTaskOnPay(OrderInfo orderInfo, List<OrderItemEntity> orderItems);

	String assignTasks(DeliveryAssignDTO dto);

	/**
	 * 按订单ID查询配送任务（回填配送员姓名），订单无任务时返回 null
	 * @param orderId 订单ID
	 * @return 配送任务或 null
	 */
	DeliveryTask getTaskByOrderId(String orderId);

	/**
	 * 按订单ID批量查询配送任务（回填配送员姓名），管理端订单列表回填用。
	 * 一个订单至多一个任务（uk_delivery_task_order），无任务的订单不在映射中
	 * @param orderIds 订单ID列表
	 * @return orderId 到配送任务的映射
	 */
	Map<String, DeliveryTask> mapByOrderIds(List<String> orderIds);

	/**
	 * 按订单ID把配送任务派给指定配送员（单任务派单，内部复用批量派单校验与出车单创建）
	 * @param orderId 订单ID
	 * @param staffId 配送员ID
	 * @return 出车单ID
	 * @throws com.aryn.cloud.common.security.handler.ArynBusinessException 任务不存在/非待派单/配送员不可接单
	 */
	String assignByOrderId(String orderId, String staffId);

	/**
	 * 统计某配送员的待处理单数（待取货+配货中+待送达）。
	 * @param staffId 配送员ID
	 * @return 待处理单数
	 */
	long countPendingTasks(String staffId);

	/**
	 * 统计某配送员今日已完成的单数（今天送达或签收）。
	 *
	 * <p>「今日」按送达/签收时间当天判定，而不是任务创建时间 ——
	 * 原工作台把 status=5/6 的总数当今日完成，实际统计的是历史累计。
	 * @param staffId 配送员ID
	 * @return 今日已完成单数
	 */
	long countTodayDoneTasks(String staffId);

	/**
	 * 司机配货时可拉进本趟的候选订单。
	 *
	 * <p>两类来源合并返回，司机在配货页一次看清「还有哪些货要拉上车」：
	 * <ul>
	 *   <li>mine=true：已派给当前司机、但不在本趟车上的未完成任务（含在其他趟次的和待派单的）</li>
	 *   <li>mine=false：本租户已付款待发货、走商城/内部配送且尚无有效配送任务的订单</li>
	 * </ul>
	 * 已在本趟车上的订单不再返回（司机看的是「还能拉什么」）。
	 *
	 * <p>「未派送」来源受租户级开关 {@link #isDriverSelfPullUnassignedAllowed()} 控制；
	 * 关掉后空 source 也不再并入该池，避免「临时新增」变成绕过开关的后门。
	 * @param tripId 当前出车单ID
	 * @param staffId 当前配送员ID
	 * @param source MINE 只看我的未完成；UNASSIGNED 只看未派送；空则两者合并
	 * @param keyword 订单号/收货人/电话模糊匹配，可空
	 * @return 候选订单（按创建时间倒序）
	 */
	List<DeliveryCandidateOrderVO> listPullCandidates(String tripId, String staffId, String source, String keyword);

	/**
	 * 当前租户是否允许司机自助拉「未派送订单」（管理端尚未派单的车单）。
	 *
	 * <p>取 order_config.driver_self_pull_unassigned，缺省/读取失败均按允许处理。
	 * 关掉后司机只能拉管理端已派给自己的任务，未派送单必须由管理端派单。
	 */
	boolean isDriverSelfPullUnassignedAllowed();

	/**
	 * 把候选订单拉进指定趟次（配货页的「今日已有未完成任务 / 未派送订单 / 临时新增」共用）。
	 *
	 * <p>三种入口在数据上收敛为同一个动作：让这些订单的配送任务归属本趟车。
	 * <ul>
	 *   <li>订单已有配送任务：改属本趟并把状态推进到本趟的进度（配货中趟次直接落配货中），
	 *       同时清掉其原趟次归属并重算原趟次单数 —— 货要跟车走，不能在两张单子上</li>
	 *   <li>订单尚无配送任务（未派送来源的历史单）：按订单快照补建任务与取货明细</li>
	 * </ul>
	 *
	 * <p>只允许拉进本司机「待配货/配货中/配送中」的趟次（已收车不能再塞货）。
	 * <p>写入前逐单校验 {@link #isDriverSelfPullUnassignedAllowed()}：候选列表只是 UI 便利，
	 * 不校验的话构造请求即可绕过开关拉走未派送单。
	 * @param tripId 目标出车单ID
	 * @param staffId 当前配送员ID
	 * @param orderIds 要拉入的订单ID列表
	 * @return 实际拉入的订单数
	 */
	int pullOrdersIntoTrip(String tripId, String staffId, List<String> orderIds);

	boolean reassign(String taskId, String staffId);

	DeliveryTask getTaskDetail(String id);

	/**
	 * 分页查询并回填配送员姓名（staffName 为非持久化派生字段，管理端列表直接展示）
	 * @param page 分页参数
	 * @param wrapper 查询条件
	 * @return 分页结果
	 */
	com.baomidou.mybatisplus.core.metadata.IPage<DeliveryTask> pageWithStaffName(
			com.baomidou.mybatisplus.extension.plugins.pagination.Page<DeliveryTask> page,
			com.baomidou.mybatisplus.core.conditions.Wrapper<DeliveryTask> wrapper);

	/**
	 * 批量回填配送员姓名，按 staffId 去重后一次查询，避免逐行查询
	 * @param tasks 任务列表，原地写入 staffName
	 */
	void fillStaffName(List<DeliveryTask> tasks);

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
	 * 按订单ID取消待派单的配送任务（货到付款订单取消时调用）
	 * @param orderId 订单ID
	 * @return 任务存在且已关闭返回 true；无关联任务返回 false
	 * @throws com.aryn.cloud.common.security.handler.ArynBusinessException 任务已派单/配送中
	 */
	boolean cancelWaitingAssignByOrderId(String orderId);

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
	 * 管理端补录送达凭证。
	 *
	 * <p>用于司机实际已送达却漏点「送达」的场景：任务必须已处于「待送达」，
	 * 且凭证图片要求与司机送达一致（1-6 张），确保确认收款始终有交付依据。
	 * 不提供从待派单/取货中直达送达的通道，避免绕过配送流程。
	 *
	 * @param taskId 任务ID
	 * @param materialIds 送达凭证素材ID列表
	 * @param remark 备注
	 * @param operatorId 操作管理员ID（记入任务日志）
	 * @return 是否成功
	 */
	boolean backfillArriveByAdmin(String taskId, List<String> materialIds, String remark, String operatorId);

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
