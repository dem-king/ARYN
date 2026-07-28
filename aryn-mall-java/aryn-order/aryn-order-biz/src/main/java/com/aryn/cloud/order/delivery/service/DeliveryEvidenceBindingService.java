package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryEvidenceMapper;
import com.aryn.cloud.upms.api.remote.RemoteMaterialAccessService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 确认订单配送凭证与 UPMS 素材的最终绑定。 */
@Service
@RequiredArgsConstructor
public class DeliveryEvidenceBindingService {

	private final OrderDeliveryEvidenceMapper evidenceMapper;

	@DubboReference
	private final RemoteMaterialAccessService materialAccessService;

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void confirmBinding(String tenantId, String reservationId, String taskId) {
		if (!materialAccessService.confirmDeliveryBinding(tenantId, reservationId, taskId)) {
			throw new ArynBusinessException("配送凭证素材绑定确认失败");
		}
		evidenceMapper.markBoundByTask(tenantId, taskId);
	}

}
