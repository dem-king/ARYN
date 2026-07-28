package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryEvidence;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import com.aryn.cloud.order.api.delivery.vo.DeliveryEvidenceCustomerVO;
import com.aryn.cloud.order.api.delivery.vo.DeliveryTaskCustomerVO;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryEvidenceMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskMapper;
import com.aryn.cloud.upms.api.remote.RemoteMaterialAccessService;
import com.aryn.cloud.upms.api.vo.MaterialAccessVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/** 客户侧商城配送进度与凭证访问。 */
@Service
@RequiredArgsConstructor
public class CustomerDeliveryService {

	private final OrderDeliveryTaskMapper taskMapper;
	private final OrderDeliveryEvidenceMapper evidenceMapper;
	@DubboReference
	private final RemoteMaterialAccessService materialAccessService;

	public DeliveryTaskCustomerVO getByOrder(String orderId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		OrderDeliveryTask task = requireTask(user, orderId);
		DeliveryTaskCustomerVO view = new DeliveryTaskCustomerVO();
		BeanUtils.copyProperties(task, view);
		List<OrderDeliveryEvidence> evidences = evidenceMapper.selectList(Wrappers.<OrderDeliveryEvidence>lambdaQuery()
			.eq(OrderDeliveryEvidence::getTenantId, user.getTenantId())
			.eq(OrderDeliveryEvidence::getTaskId, task.getId())
			.eq(OrderDeliveryEvidence::getBindingStatus, "BOUND")
			.orderByAsc(OrderDeliveryEvidence::getSortNo));
		view.setEvidences(evidences.stream().map(this::toEvidenceView).toList());
		return view;
	}

	public MaterialAccessVO getEvidenceAccess(String orderId, String evidenceId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		OrderDeliveryTask task = requireTask(user, orderId);
		OrderDeliveryEvidence evidence = evidenceMapper.selectOne(Wrappers.<OrderDeliveryEvidence>lambdaQuery()
			.eq(OrderDeliveryEvidence::getTenantId, user.getTenantId())
			.eq(OrderDeliveryEvidence::getTaskId, task.getId())
			.eq(OrderDeliveryEvidence::getId, evidenceId)
			.eq(OrderDeliveryEvidence::getBindingStatus, "BOUND"));
		if (evidence == null) {
			throw new ArynBusinessException("配送凭证不存在");
		}
		return materialAccessService.getTemporaryAccess(user.getTenantId(), evidence.getMaterialId());
	}

	private OrderDeliveryTask requireTask(ArynUser user, String orderId) {
		OrderDeliveryTask task = taskMapper.selectCustomerTask(user.getTenantId(), user.getUserId(), orderId);
		if (task == null) {
			throw new ArynBusinessException("商城配送任务不存在");
		}
		return task;
	}

	private DeliveryEvidenceCustomerVO toEvidenceView(OrderDeliveryEvidence evidence) {
		DeliveryEvidenceCustomerVO view = new DeliveryEvidenceCustomerVO();
		view.setId(evidence.getId());
		view.setEvidenceType(evidence.getEvidenceType());
		view.setSortNo(evidence.getSortNo());
		return view;
	}
}
