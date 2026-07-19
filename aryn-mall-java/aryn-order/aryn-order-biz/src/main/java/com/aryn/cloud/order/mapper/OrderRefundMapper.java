
package com.aryn.cloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.order.api.dto.OrderStatisticsDTO;
import com.aryn.cloud.order.api.entity.OrderRefund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商城退款单
 *
 * @author 雨滴kian
 * @date 2022/5/31
 */
@Mapper
public interface OrderRefundMapper extends BaseMapper<OrderRefund> {

	/**
	 * 商城退款单列表
	 * @param page
	 * @param orderRefund
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.OrderRefund>
	 */
	IPage<OrderRefund> selectAdminPage(Page page, @Param("query") OrderRefund orderRefund);

	OrderRefund selectRefundById(Serializable id);

	OrderRefund selectRefundByIdAndUser(@Param("id") String id, @Param("userId") String userId);

	/**
	 * 通过子订单ID查询退款单
	 * @param orderItemId
	 * @author 雨滴kian
	 * @date 2022/7/1
	 * @return: com.aryn.cloud.mall.common.entity.OrderRefund
	 */
	OrderRefund selectByOrderItemId(String orderItemId);

	/**
	 * 退款金额查询
	 * @param request
	 * @return
	 */
	BigDecimal selectRefundAmount(@Param("query") OrderStatisticsDTO request);

	/**
	 * 退款数量查询
	 * @param request
	 * @return
	 */
	Integer selectRefundCount(@Param("query") OrderStatisticsDTO request);

}
