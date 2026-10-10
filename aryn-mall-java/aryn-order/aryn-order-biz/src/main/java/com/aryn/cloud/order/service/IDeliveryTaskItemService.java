
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.entity.DeliveryTaskItem;

import java.util.List;

/**
 * 取货明细
 *
 * @author aryn
 * @since 2025/7/31
 */
public interface IDeliveryTaskItemService extends IService<DeliveryTaskItem> {

	/**
	 * 根据任务ID查询取货明细
	 * @param taskId 任务ID
	 * @return 明细列表
	 */
	List<DeliveryTaskItem> listByTaskId(String taskId);

	/**
	 * 批量回填商品分类名（读时聚合：order_item.spuId → 商品域 Dubbo 查类目名，不入库）。
	 * <p>整批只发起一次商品域调用，调用方应把多个任务的明细合并后传入，不要逐任务调用。
	 * @param items 待回填的明细列表（原位修改 categoryName）
	 */
	void fillCategoryName(List<DeliveryTaskItem> items);

	/**
	 * 逐项确认取货
	 * @param itemId 明细ID
	 * @param staffId 当前配送员ID
	 * @return 是否成功
	 */
	boolean pick(String itemId, String staffId);

	/**
	 * 取消确认取货
	 * @param itemId 明细ID
	 * @param staffId 当前配送员ID
	 * @return 是否成功
	 */
	boolean unpick(String itemId, String staffId);

	/**
	 * 批量确认/取消取货。
	 *
	 * <p>配货视图按「商品 + 规格」合并展示（整趟车一次配齐），一次勾选命中多条明细，
	 * 逐条发起请求在整趟车场景下会放大成几十次往返。这里改为一次性校验并批量落库，
	 * 校验口径与 {@link #pick} 一致：明细必须属于该配送员的配货中出车单。
	 * @param tripId 出车单ID
	 * @param itemIds 明细ID列表
	 * @param picked true 确认取货，false 取消确认
	 * @param staffId 当前配送员ID
	 * @return 本次实际变更的明细数
	 */
	int batchPick(String tripId, List<String> itemIds, boolean picked, String staffId);

	/**
	 * 校验某出车单下所有任务的所有明细是否全部已取
	 * @param tripId 出车单ID
	 * @return 是否全部已取
	 */
	boolean allPicked(String tripId);

	/**
	 * 校验单个任务的明细是否全部已取。
	 *
	 * <p>用于「已出发」趟次重复点出发时，只校验后来新并入的那几张单，
	 * 而不是把整趟（含早已在配送路上的单）重新校验一遍。
	 * @param taskId 配送任务ID
	 * @return 是否全部已取
	 */
	boolean allPickedByTask(String taskId);

}