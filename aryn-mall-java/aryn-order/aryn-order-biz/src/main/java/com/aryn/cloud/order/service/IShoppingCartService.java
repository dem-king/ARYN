
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.dto.ShoppingCartBatchAddDTO;
import com.aryn.cloud.order.api.dto.ShoppingCartCreateDTO;
import com.aryn.cloud.order.api.dto.ShoppingCartTransferDTO;
import com.aryn.cloud.order.api.dto.ShoppingCartUpdateDTO;
import com.aryn.cloud.order.api.entity.ShoppingCart;
import com.aryn.cloud.order.api.vo.ShoppingCartBatchAddVO;

import java.util.List;

/**
 * 购物车
 *
 * @author 雨滴kian
 * @since 2022/3/17 14:56
 */
public interface IShoppingCartService extends IService<ShoppingCart> {

	/**
	 * 购物车列表
	 *
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @param shoppingCart
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.ShoppingCart>
	 */
	List<ShoppingCart> apiPage(Page page, ShoppingCart shoppingCart);

	/**
	 * 保存
	 *
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @param shoppingCart
	 * @return: boolean
	 */
	boolean saveShoppingCart(String userId, ShoppingCartCreateDTO request);

	/**
	 * 批量加购：逐项独立提交，返回成功/失败清单（部分成功语义）。
	 *
	 * <p>用于首页「清单还差 / 今日补给单」勾选多项后一次加入，
	 * 避免前端串行调用 N 次单条接口。
	 *
	 * @param userId  当前用户
	 * @param request 批量请求（上限 {@link ShoppingCartBatchAddDTO#MAX_BATCH_SIZE}）
	 * @return 成功项数与失败明细
	 */
	ShoppingCartBatchAddVO batchAdd(String userId, ShoppingCartBatchAddDTO request);

	/**
	 * 删除购物车
	 * @param userId 用户id
	 * @param skuIds SKU ID列表
	 * @return
	 */
	boolean clear(String userId, List<String> skuIds);

	/**
	 * 更新购物车
	 * @param userId 用户ID
	 * @param request 修改请求
	 * @return
	 */
	boolean updateShoppingCart(String userId, ShoppingCartUpdateDTO request);

	boolean removeByUserId(String userId, List<String> ids);

	/**
	 * 把当前用户的购物车行转入指定靠港计划（C 端「转入本次靠港」）。
	 *
	 * <p>目标靠港必须仍可下单；同用户同 SKU 在目标靠港已有行时合并数量而非撞唯一键。
	 *
	 * @param userId  当前用户
	 * @param request 目标靠港与购物车行 ID
	 * @return 实际处理的行数（含被合并删除的行）
	 */
	int transferToVesselCall(String userId, ShoppingCartTransferDTO request);

	/**
	 * 把船舶上「无靠港归属或挂在已结束靠港」的购物车行顺延到新靠港（vessel 模块申报/排产生效时调用）。
	 *
	 * <p>与 {@link #transferToVesselCall} 共用迁移逻辑；差异是船舶维度（该船全部用户）、
	 * 不校验靠港可下单性（调用方已保证），全部参数显式传入、不依赖调用链路租户上下文。
	 *
	 * @return 迁移的行数
	 */
	int reattachRowsToVesselCall(String tenantId, String vesselId, String vesselCallId, List<String> staleCallIds);

}
