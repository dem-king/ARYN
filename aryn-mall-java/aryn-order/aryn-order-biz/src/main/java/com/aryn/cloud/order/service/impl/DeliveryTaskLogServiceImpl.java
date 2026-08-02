
package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.order.api.entity.DeliveryTaskLog;
import com.aryn.cloud.order.mapper.DeliveryTaskLogMapper;
import com.aryn.cloud.order.service.IDeliveryTaskLogService;
import org.springframework.stereotype.Service;

@Service
public class DeliveryTaskLogServiceImpl extends ServiceImpl<DeliveryTaskLogMapper, DeliveryTaskLog>
		implements IDeliveryTaskLogService {

}