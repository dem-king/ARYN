package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.DistributionRefundRecord;
import com.aryn.cloud.promotion.mapper.DistributionRefundRecordMapper;
import com.aryn.cloud.promotion.service.IDistributionRefundRecordService;
import org.springframework.stereotype.Service;

@Service
public class DistributionRefundRecordServiceImpl
		extends ServiceImpl<DistributionRefundRecordMapper, DistributionRefundRecord>
		implements IDistributionRefundRecordService {
}
