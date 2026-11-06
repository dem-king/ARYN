package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.promotion.api.entity.SalesRefundRecord;
import com.aryn.cloud.promotion.mapper.SalesRefundRecordMapper;
import com.aryn.cloud.promotion.service.ISalesRefundRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 销售业绩退款幂等服务实现
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesRefundRecordServiceImpl extends ServiceImpl<SalesRefundRecordMapper, SalesRefundRecord>
	implements ISalesRefundRecordService {

	@Override
	public SalesRefundRecord getByRefundNo(String refundNo) {
		if (refundNo == null || refundNo.isBlank()) {
			return null;
		}
		return this.getOne(Wrappers.<SalesRefundRecord>lambdaQuery()
			.eq(SalesRefundRecord::getRefundNo, refundNo)
			.last("limit 1"));
	}

	@Override
	public int reopenAbandonedByBizOrderId(String bizOrderId) {
		if (bizOrderId == null || bizOrderId.isBlank()) {
			return 0;
		}
		int rows = this.baseMapper.update(null, Wrappers.<SalesRefundRecord>lambdaUpdate()
			.set(SalesRefundRecord::getApplied, CommonConstants.NO)
			.set(SalesRefundRecord::getAppliedTime, null)
			.eq(SalesRefundRecord::getBizOrderId, bizOrderId)
			.eq(SalesRefundRecord::getApplied, CommonConstants.YES));
		if (rows > 0) {
			log.info("重开已放弃应用的销售退款记录 bizOrderId={}, rows={}", bizOrderId, rows);
		}
		return rows;
	}

}
