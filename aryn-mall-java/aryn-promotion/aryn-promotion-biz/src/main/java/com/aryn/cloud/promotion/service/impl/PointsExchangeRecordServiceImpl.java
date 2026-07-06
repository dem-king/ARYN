
package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.PointsExchangeRecord;
import com.aryn.cloud.promotion.mapper.PointsExchangeRecordMapper;
import com.aryn.cloud.promotion.service.IPointsExchangeRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 积分兑换记录 ServiceImpl
 *
 * @author aryn
 */
@Service
@RequiredArgsConstructor
public class PointsExchangeRecordServiceImpl extends ServiceImpl<PointsExchangeRecordMapper, PointsExchangeRecord>
		implements IPointsExchangeRecordService {

}