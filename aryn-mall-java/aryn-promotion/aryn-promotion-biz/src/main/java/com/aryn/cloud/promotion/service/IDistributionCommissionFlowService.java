package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.DistributionCommissionFlow;

public interface IDistributionCommissionFlowService extends IService<DistributionCommissionFlow> {

    IPage<DistributionCommissionFlow> getUserPage(Page page, String userId);
}
