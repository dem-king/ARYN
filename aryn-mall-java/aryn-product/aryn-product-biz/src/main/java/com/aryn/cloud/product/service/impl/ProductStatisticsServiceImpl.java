package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.product.api.dto.ProductStatisticsDTO;
import com.aryn.cloud.product.api.vo.*;
import com.aryn.cloud.product.mapper.ProductStatisticsMapper;
import com.aryn.cloud.product.service.IProductStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductStatisticsServiceImpl implements IProductStatisticsService {

	private final ProductStatisticsMapper productStatisticsMapper;

	@Override
	public List<ProductSalesRankVO> getProductSalesTop10() {
		return productStatisticsMapper.getProductSalesTop10();
	}

	@Override
	public ProductOverviewVO getProductOverview() {
		return productStatisticsMapper.getProductOverview();
	}

	@Override
	public List<ProductVisitTrendVO> getProductVisitTrend(ProductStatisticsDTO dto) {
		LocalDateTime startTime = dto.getStartTime();
		LocalDateTime endTime = dto.getEndTime();
		String format;
		List<String> timePoints = new ArrayList<>();
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		if (startTime.toLocalDate().equals(endTime.toLocalDate())) {
			// 同一天，按小时分组
			format = "%H";
			// 生成小时列表 00-23 (或者 startHour - endHour)
			int startHour = startTime.getHour();
			int endHour = endTime.getHour();
			for (int i = startHour; i <= endHour; i++) {
				timePoints.add(String.format("%02d", i));
			}
		}
		else {
			// 不同天，按日期分组
			format = "%Y-%m-%d";
			LocalDateTime temp = startTime;
			// 按天遍历
			while (!temp.toLocalDate().isAfter(endTime.toLocalDate())) {
				timePoints.add(temp.format(dateFormatter));
				temp = temp.plusDays(1);
			}
		}

		List<ProductVisitTrendVO> dbList = productStatisticsMapper.getProductVisitTrend(dto, format);
		Map<String, ProductVisitTrendVO> dbMap = dbList.stream()
			.collect(Collectors.toMap(ProductVisitTrendVO::getTimePoint, v -> v, (v1, v2) -> v1));

		List<ProductVisitTrendVO> result = new ArrayList<>();
		for (String tp : timePoints) {
			ProductVisitTrendVO vo = dbMap.get(tp);
			if (vo == null) {
				vo = new ProductVisitTrendVO();
				vo.setTimePoint(tp);
				vo.setPv(0);
				vo.setUv(0);
			}
			result.add(vo);
		}
		return result;
	}

	@Override
	public List<ProductLowStockVO> getLowStockTop10() {
		return productStatisticsMapper.getLowStockTop10();
	}

	@Override
	public List<ProductVisitRankVO> getProductVisitTop10(ProductStatisticsDTO dto) {
		return productStatisticsMapper.getProductVisitTop10(dto);
	}

	@Override
	public List<ProductPraiseRankVO> getProductPraiseTop10(ProductStatisticsDTO dto) {
		return productStatisticsMapper.getProductPraiseTop10(dto);
	}

}
