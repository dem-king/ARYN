package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.dto.DashboardTargetDTO;
import com.aryn.cloud.order.api.entity.DashboardTarget;
import com.aryn.cloud.order.api.vo.DashboardTargetVO;

/**
 * 管理端概览月度目标。
 */
public interface IDashboardTargetService extends IService<DashboardTarget> {

	DashboardTargetVO getTargetOverview(String month);

	DashboardTargetVO saveTarget(DashboardTargetDTO dto);

}
