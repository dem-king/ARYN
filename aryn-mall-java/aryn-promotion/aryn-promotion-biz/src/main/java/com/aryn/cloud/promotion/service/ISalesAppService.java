package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBind;
import com.aryn.cloud.promotion.api.entity.SalesOrder;
import com.aryn.cloud.promotion.api.entity.SalesTransferReward;
import com.aryn.cloud.promotion.api.vo.AppSalesCustomerVO;
import com.aryn.cloud.promotion.api.vo.AppSalesOrderVO;
import com.aryn.cloud.promotion.api.vo.AppSalesWorkbenchVO;
import com.aryn.cloud.promotion.api.vo.SalesTransferRewardVO;

/**
 * 小程序销售域服务
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface ISalesAppService {

	/**
	 * 销售工作台汇总（当前会员非在职销售时 isSales=false）
	 * @param memberUserId 当前会员ID
	 * @return 工作台汇总
	 */
	AppSalesWorkbenchVO workbench(String memberUserId);

	/**
	 * 我的客户分页
	 * @param page 分页参数
	 * @param memberUserId 当前会员ID
	 * @return 分页结果
	 */
	IPage<AppSalesCustomerVO> customerPage(Page<SalesCustomerBind> page, String memberUserId);

	/**
	 * 我的业绩明细分页
	 * @param page 分页参数
	 * @param memberUserId 当前会员ID
	 * @return 分页结果
	 */
	IPage<AppSalesOrderVO> orderPage(Page<SalesOrder> page, String memberUserId);

	/**
	 * 我的转交奖励分页（分销员视角）
	 * @param page 分页参数
	 * @param memberUserId 当前会员ID
	 * @return 分页结果
	 */
	IPage<SalesTransferRewardVO> transferRewardPage(Page<SalesTransferReward> page, String memberUserId);

}
