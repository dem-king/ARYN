package com.aryn.cloud.order.service.impl;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.dto.OrderInfoDTO;
import com.aryn.cloud.order.api.dto.OrderStatisticsDTO;
import com.aryn.cloud.order.api.vo.OrderStatisticsVO;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderRefundMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderStatisticsQueryService {

	private final OrderInfoMapper orderInfoMapper;

	private final OrderRefundMapper orderRefundMapper;

	public BigDecimal getPaySumStatistics(OrderInfoDTO orderInfoDTO) {
		return orderInfoMapper.selectPaySumStatistics(orderInfoDTO);
	}

	public List<Map<String, Object>> statistics() {
		List<Map<String, Object>> reList = new ArrayList<>();
		LocalDateTime now = LocalDateTime.now();
		for (int i = 11; i >= 0; i--) {
			LocalDateTime offset = LocalDateTimeUtil.offset(now, -i, ChronoUnit.MONTHS);
			int year = offset.getYear();
			Month month = offset.getMonth();
			LocalDateTime startOfMonth = LocalDateTime.of(year, month, 1, 0, 0, 0);
			LocalDateTime endOfMonth = startOfMonth.withDayOfMonth(month.length(false))
				.withHour(23)
				.withMinute(59)
				.withSecond(59);
			Map<String, Object> rtMap = new HashMap<>();
			OrderInfoDTO orderInfoDTO = new OrderInfoDTO();
			orderInfoDTO.setPayStatus(CommonConstants.YES);
			orderInfoDTO.setPaymentType(MallOrderConstants.PAYMENT_TYPE_1);
			orderInfoDTO.setBeginTime(startOfMonth);
			orderInfoDTO.setEndTime(endOfMonth);
			BigDecimal wxPaySum = orderInfoMapper.selectPaySumStatistics(orderInfoDTO);

			orderInfoDTO.setPaymentType(MallOrderConstants.PAYMENT_TYPE_2);
			BigDecimal aliPaySum = orderInfoMapper.selectPaySumStatistics(orderInfoDTO);
			rtMap.put("wxCount", wxPaySum);
			rtMap.put("aliCount", aliPaySum);
			rtMap.put("date", LocalDateTimeUtil.format(offset, DatePattern.NORM_MONTH_PATTERN));
			reList.add(rtMap);
		}
		return reList;
	}

	public Map<String, Object> merchantStatistics(OrderStatisticsDTO request) {
		Map<String, Object> rtMap = new HashMap<>();
		rtMap.put("orderAmount", orderInfoMapper.selectPayAmount(request));
		rtMap.put("orderCount", orderInfoMapper.selectPayCount(request));
		rtMap.put("refundAmount", orderRefundMapper.selectRefundAmount(request));
		rtMap.put("refundCount", orderRefundMapper.selectRefundCount(request));
		return rtMap;
	}

	public List<OrderStatisticsVO> payTypeStatistics(OrderStatisticsDTO orderStatisticsDTO) {
		return orderInfoMapper.payTypeStatistics(orderStatisticsDTO);
	}

	public List<OrderStatisticsVO> channelTypeStatistics(OrderStatisticsDTO orderStatisticsDTO) {
		return orderInfoMapper.channelTypeStatistics(orderStatisticsDTO);
	}

}
