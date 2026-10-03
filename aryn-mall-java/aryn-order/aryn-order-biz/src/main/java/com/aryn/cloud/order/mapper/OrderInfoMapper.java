
package com.aryn.cloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.order.api.dto.OrderInfoDTO;
import com.aryn.cloud.order.api.dto.OrderStatisticsDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.vo.OrderStatisticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 订单
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:32
 */
@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {

	/**
	 * 分页查询订单列表
	 * @param page
	 * @param orderInfo
	 * @author 雨滴kian
	 * @date 2022/6/11
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.OrderInfo>
	 */
	IPage<OrderInfo> selectAdminPage(Page page, @Param("query") OrderInfo orderInfo);

	/**
	 * 管理端订单导出列表：查询条件与 {@link #selectAdminPage} 完全一致，仅不分页
	 * @param orderInfo 查询条件
	 * @return 订单列表（不含商品行，商品行由调用方批量查询回填）
	 */
	List<OrderInfo> selectExportList(@Param("query") OrderInfo orderInfo);

	/**
	 * 导出前计数：与 {@link #selectExportList} 条件一致，用于超限拦截
	 * @param orderInfo 查询条件
	 * @return 符合条件的订单数
	 */
	int countExportList(@Param("query") OrderInfo orderInfo);

	/**
	 * 订单详情
	 * @param id
	 * @author 雨滴kian
	 * @date 2022/6/11
	 * @return: com.aryn.cloud.mall.common.entity.OrderInfo
	 */
	OrderInfo selectOrderById(Serializable id);

	OrderInfo selectOrderByIdAndUser(@Param("id") String id, @Param("userId") String userId);

	/**
	 * 支付金额统计
	 * @param orderInfoDTO
	 * @return
	 */
	BigDecimal selectPaySumStatistics(@Param("query") OrderInfoDTO orderInfoDTO);

	/**
	 * 订单列表
	 * @param page
	 * @param orderInfo
	 * @author 雨滴kian
	 * @date 2022/6/11
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.OrderInfo>
	 */
	IPage<OrderInfo> selectApiPage(Page page, @Param("query") OrderInfo orderInfo);

	/**
	 * 支付金额查询
	 * @param request
	 * @return
	 */
	BigDecimal selectPayAmount(@Param("query") OrderStatisticsDTO request);

	/**
	 * 支付数量查询
	 * @param request
	 * @return
	 */
	Integer selectPayCount(@Param("query") OrderStatisticsDTO request);

	List<OrderStatisticsVO> payTypeStatistics(@Param("query") OrderStatisticsDTO orderStatisticsDTO);

	List<OrderStatisticsVO> channelTypeStatistics(@Param("query") OrderStatisticsDTO orderStatisticsDTO);

}
