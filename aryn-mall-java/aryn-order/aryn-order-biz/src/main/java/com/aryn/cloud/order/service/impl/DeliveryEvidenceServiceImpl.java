
package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.order.api.entity.DeliveryEvidence;
import com.aryn.cloud.order.mapper.DeliveryEvidenceMapper;
import com.aryn.cloud.order.service.IDeliveryEvidenceService;
import org.springframework.stereotype.Service;

@Service
public class DeliveryEvidenceServiceImpl extends ServiceImpl<DeliveryEvidenceMapper, DeliveryEvidence>
		implements IDeliveryEvidenceService {

}