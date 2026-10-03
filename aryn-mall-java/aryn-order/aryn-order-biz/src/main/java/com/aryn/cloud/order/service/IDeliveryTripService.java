
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.entity.DeliveryTrip;

/**
 * 出车单
 *
 * @author aryn
 * @since 2025/7/31
 */
public interface IDeliveryTripService extends IService<DeliveryTrip> {

	/**
	 * 出车单详情（含所有task和item）
	 * @param id 出车单ID
	 * @return 出车单
	 */
	DeliveryTrip getTripDetail(String id);

	/**
	 * 分页查询并回填配送员姓名（staffName 为非持久化派生字段，管理端列表直接展示）
	 * @param page 分页参数
	 * @param wrapper 查询条件
	 * @return 分页结果
	 */
	com.baomidou.mybatisplus.core.metadata.IPage<DeliveryTrip> pageWithStaffName(
			com.baomidou.mybatisplus.extension.plugins.pagination.Page<DeliveryTrip> page,
			com.baomidou.mybatisplus.core.conditions.Wrapper<DeliveryTrip> wrapper);

	/**
	 * 开始配货
	 * @param tripId 出车单ID
	 * @param staffId 当前配送员ID
	 * @return 是否成功
	 */
	boolean startLoading(String tripId, String staffId);

	/**
	 * 装货完毕出发
	 * @param tripId 出车单ID
	 * @param staffId 当前配送员ID
	 * @return 是否成功
	 */
	boolean depart(String tripId, String staffId);

	/**
	 * 调整送货顺序
	 * @param tripId 出车单ID
	 * @param staffId 当前配送员ID
	 * @param taskIds 按顺序排列的任务ID
	 * @return 是否成功
	 */
	boolean adjustSort(String tripId, String staffId, java.util.List<String> taskIds);

	/**
	 * 获取配送员当前进行中的出车单
	 * @param staffId 配送员ID
	 * @return 出车单
	 */
	DeliveryTrip getActiveTrip(String staffId);

	/**
	 * 全部任务结清（已送达/已签收/已取消）后自动完成出车单。
	 *
	 * <p>出车单是配送员的一趟车，任务送达即司机侧履约结束；
	 * 客户签收是订单域事件，不应把出车单长期挂在「配送中」。
	 * @param tripId 出车单ID
	 * @return 本次调用是否把出车单置为已完成（已完结或仍有在途任务返回 false）
	 */
	boolean completeIfAllTasksSettled(String tripId);

}