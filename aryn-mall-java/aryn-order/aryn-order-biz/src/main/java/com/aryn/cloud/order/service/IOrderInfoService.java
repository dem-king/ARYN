
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.order.api.dto.*;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.vo.OrderStatisticsVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 订单
 *
 * @author 雨滴kian
 * @since 2022/3/7 14:18
 */
public interface IOrderInfoService extends IService<OrderInfo> {

	/**
	 * 分页查询订单列表
	 * @param page
	 * @param orderInfo
	 * @author 雨滴kian
	 * @date 2022/6/11
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.OrderInfo>
	 */
	IPage<OrderInfo> adminPage(Page page, OrderInfo orderInfo);

	/**
	 * 订单详情
	 * @param id
	 * @author 雨滴kian
	 * @date 2022/6/11
	 * @return: com.aryn.cloud.mall.common.entity.OrderInfo
	 */
	OrderInfo getOrderById(String id);

	/**
	 * 订单发货
	 * @param request
	 * @author 雨滴kian
	 * @date 2022/6/11
	 * @return: java.lang.Object
	 */
	boolean deliverOrder(OrderDeliveryDTO request);

	/**
	 * 订单取消
	 * @param orderInfo
	 * @author 雨滴kian
	 * @date 2022/6/13
	 * @return: boolean
	 */
	String cancelOrder(OrderInfo orderInfo);

	/**
	 * 支付金额统计
	 * @param orderInfoDTO
	 * @return
	 */
	BigDecimal getPaySumStatistics(OrderInfoDTO orderInfoDTO);

	/**
	 * 订单列表
	 * @param page
	 * @param orderInfo
	 * @author 雨滴kian
	 * @date 2022/6/11
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.OrderInfo>
	 */
	IPage<OrderInfo> apiPage(Page page, OrderInfo orderInfo);

	/**
	 * 创建订单
	 * @param createOrderDTO
	 * @author 雨滴kian
	 * @date 2022/6/11
	 * @return: com.aryn.cloud.mall.common.entity.OrderInfo
	 */
	OrderInfo createOrder(CreateOrderDTO createOrderDTO);

	/**
	 * 订单确认收货/订单自提
	 * @param orderInfo
	 * @author 雨滴kian
	 * @date 2022/6/11
	 * @return: boolean
	 */
	boolean receiveOrder(OrderInfo orderInfo);

	/**
	 * 预支付(调用统一下单接口)
	 * @param prepayDTO
	 * @author 雨滴kian
	 * @date 2022/6/11
	 * @return: com.aryn.cloud.mall.common.entity.OrderInfo
	 */
	Result<Object> prepay(PrepayDTO prepayDTO);

	/**
	 * 订单评价
	 * @param id 订单id
	 * @param orderAppraiseList 订单评价列表
	 * @return boolean
	 */
	boolean appraiseOrder(String id, List<OrderAppraiseDTO> orderAppraiseList);

	/**
	 * 订单统计
	 * @return
	 */
	List<Map<String, Object>> statistics();

	/**
	 * 订单结算
	 * @param settlementOrderDTO
	 * @return
	 */
	OrderInfo settlementOrder(SettlementOrderDTO settlementOrderDTO);

	/**
	 * 商户订单统计
	 * @param request 订单统计请求
	 * @return
	 */
	Map<String, Object> merchantStatistics(OrderStatisticsDTO request);

	/**
	 * 支付类型统计
	 * @param orderStatisticsDTO
	 * @return
	 */
	List<OrderStatisticsVO> payTypeStatistics(OrderStatisticsDTO orderStatisticsDTO);

	List<OrderStatisticsVO> channelTypeStatistics(OrderStatisticsDTO orderStatisticsDTO);

	/**
	 * 系统自动评价订单
	 * @param orderInfo
	 */
	boolean autoAppraiseOrder(OrderInfo orderInfo);

}
