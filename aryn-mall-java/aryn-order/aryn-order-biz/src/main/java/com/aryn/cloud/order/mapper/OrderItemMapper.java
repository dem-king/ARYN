
package com.aryn.cloud.order.mapper;

import com.aryn.cloud.order.api.dto.GoodsSalesRankDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.io.Serializable;
import java.util.List;

/**
 * 子订单
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:32
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemEntity> {

	/**
	 * 通过订单ID查询子订单列表
	 *
	 * @author 雨滴kian
	 * @date 2022/6/11
	 * @param orderId
	 * @return: java.util.List<com.aryn.cloud.mall.common.entity.OrderItem>
	 */
	List<OrderItemEntity> selectByOrderId(String orderId);

	/**
	 * 通过订单ID集合批量查询子订单（导出场景避免逐单 N+1）
	 *
	 * @param orderIds 订单主键集合
	 * @return 子订单列表，按 order_id 排序
	 */
	List<OrderItemEntity> selectByOrderIds(@Param("orderIds") List<String> orderIds);

	/**
	 * 查询子订单详情
	 *
	 * @author 雨滴kian
	 * @date 2022/7/2
	 * @param id
	 * @return: com.aryn.cloud.mall.common.entity.OrderItem
	 */
	OrderItemEntity selectOrderItemById(Serializable id);

	/**
	 * 常购统计：用户近 N 天有效订单的 SKU 聚合（按累计件数倒序）。
	 */
	List<com.aryn.cloud.order.api.vo.FrequentPurchaseVO> selectFrequentPurchase(
			@org.apache.ibatis.annotations.Param("tenantId") String tenantId,
			@org.apache.ibatis.annotations.Param("userId") String userId,
			@org.apache.ibatis.annotations.Param("startTime") java.time.LocalDateTime startTime,
			@org.apache.ibatis.annotations.Param("limit") int limit);

	/**
	 * 热销榜统计：租户近 N 天有效订单的 SPU 聚合（按累计件数倒序）。
	 *
	 * <p>口径与 {@link #selectFrequentPurchase} 一致：order_info.status IN ('2','3','4')、
	 * 订单与明细均未删除、同租户；租户由 SQL 显式传参保证，不依赖调用方线程里的租户上下文。
	 *
	 * @param tenantId 租户
	 * @param days 统计窗口天数（服务层已钳制 1~3650）
	 * @param limit 返回条数（服务层已钳制 1~50）
	 * @return 热销榜条目，按累计成交件数倒序
	 */
	List<GoodsSalesRankDTO> selectGoodsSalesRank(@Param("tenantId") String tenantId, @Param("days") int days,
			@Param("limit") int limit);

}
