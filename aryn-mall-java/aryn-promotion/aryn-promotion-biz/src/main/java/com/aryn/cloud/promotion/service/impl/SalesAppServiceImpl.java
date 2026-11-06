package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBind;
import com.aryn.cloud.promotion.api.entity.SalesOrder;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
import com.aryn.cloud.promotion.api.entity.SalesTransferReward;
import com.aryn.cloud.promotion.api.vo.AppSalesCustomerVO;
import com.aryn.cloud.promotion.api.vo.AppSalesOrderVO;
import com.aryn.cloud.promotion.api.vo.AppSalesWorkbenchVO;
import com.aryn.cloud.promotion.api.vo.SalesTransferRewardVO;
import com.aryn.cloud.promotion.service.ISalesAppService;
import com.aryn.cloud.promotion.service.ISalesCustomerBindService;
import com.aryn.cloud.promotion.service.ISalesOrderService;
import com.aryn.cloud.promotion.service.ISalesStaffService;
import com.aryn.cloud.promotion.service.ISalesTransferRewardService;
import com.aryn.cloud.promotion.vo.SalesWorkbenchStatsVO;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 小程序销售域服务实现
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesAppServiceImpl implements ISalesAppService {

	private final ISalesStaffService salesStaffService;

	private final ISalesCustomerBindService salesCustomerBindService;

	private final ISalesOrderService salesOrderService;

	private final ISalesTransferRewardService salesTransferRewardService;

	private final SalesUserEnricher salesUserEnricher;

	@Override
	public AppSalesWorkbenchVO workbench(String memberUserId) {
		SalesStaff staff = salesStaffService.getActiveByMemberUserId(memberUserId);
		AppSalesWorkbenchVO vo = new AppSalesWorkbenchVO();
		if (staff == null) {
			vo.setIsSales(Boolean.FALSE);
			vo.setCustomerCount(0L);
			vo.setMonthOrderCount(0);
			vo.setMonthTradeAmount(BigDecimal.ZERO);
			vo.setMonthCommission(BigDecimal.ZERO);
			vo.setTotalCommission(BigDecimal.ZERO);
			return vo;
		}
		vo.setIsSales(Boolean.TRUE);
		vo.setStaffId(staff.getId());
		vo.setStaffName(staff.getStaffName());
		vo.setDeptName(staff.getDeptName());
		vo.setCustomerCount(salesCustomerBindService.countBySalesStaffId(staff.getId()));
		SalesWorkbenchStatsVO stats = salesOrderService.workbenchStats(staff.getId());
		vo.setMonthOrderCount(stats.getMonthOrderCount());
		vo.setMonthTradeAmount(stats.getMonthTradeAmount());
		vo.setMonthCommission(stats.getMonthCommission());
		vo.setTotalCommission(stats.getTotalCommission());
		// 推广小程序码由 user 域生成（WxMaService 只在 user 域），此处返回 null 由前端降级
		vo.setQrcodeUrl(null);
		return vo;
	}

	@Override
	public IPage<AppSalesCustomerVO> customerPage(Page<SalesCustomerBind> page, String memberUserId) {
		SalesStaff staff = salesStaffService.getActiveByMemberUserId(memberUserId);
		if (staff == null) {
			return emptyPage(page);
		}
		IPage<SalesCustomerBind> bindPage = salesCustomerBindService.page(page,
				Wrappers.<SalesCustomerBind>lambdaQuery()
					.eq(SalesCustomerBind::getSalesStaffId, staff.getId())
					.orderByDesc(SalesCustomerBind::getBindTime));
		List<String> customerIds = bindPage.getRecords().stream().map(SalesCustomerBind::getCustomerUserId).toList();
		Map<String, UserInfoVO> userMap = salesUserEnricher.getUserMap(customerIds);
		return bindPage.convert(bind -> {
			AppSalesCustomerVO vo = new AppSalesCustomerVO();
			vo.setCustomerUserId(bind.getCustomerUserId());
			vo.setBindSource(bind.getBindSource());
			vo.setBindTime(bind.getBindTime());
			UserInfoVO user = userMap.get(bind.getCustomerUserId());
			if (user != null) {
				vo.setNickname(user.getNickname());
				vo.setAvatar(user.getAvatarUrl());
			}
			return vo;
		});
	}

	@Override
	public IPage<AppSalesOrderVO> orderPage(Page<SalesOrder> page, String memberUserId) {
		SalesStaff staff = salesStaffService.getActiveByMemberUserId(memberUserId);
		if (staff == null) {
			return emptyPage(page);
		}
		return salesOrderService.selectAppPage(page, staff.getId());
	}

	@Override
	public IPage<SalesTransferRewardVO> transferRewardPage(Page<SalesTransferReward> page, String memberUserId) {
		return salesTransferRewardService.selectAppPage(page, memberUserId);
	}

	private <T> IPage<T> emptyPage(Page<?> source) {
		return new Page<T>(source.getCurrent(), source.getSize(), 0L);
	}

}
