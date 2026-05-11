package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.DistributionCommissionFlow;
import com.aryn.cloud.promotion.mapper.DistributionCommissionFlowMapper;
import com.aryn.cloud.promotion.service.IDistributionCommissionFlowService;
import org.springframework.stereotype.Service;

@Service
public class DistributionCommissionFlowServiceImpl
    extends ServiceImpl<DistributionCommissionFlowMapper, DistributionCommissionFlow>
    implements IDistributionCommissionFlowService {

    @Override
    public IPage<DistributionCommissionFlow> getUserPage(Page page, String userId) {
        return this.page(page, Wrappers.<DistributionCommissionFlow>lambdaQuery()
            .eq(DistributionCommissionFlow::getUserId, userId)
            .orderByDesc(DistributionCommissionFlow::getCreateTime));
    }
}
