package com.aryn.cloud.order.delivery.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryEvidence;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTaskLog;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryEvidenceMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskLogMapper;
import com.aryn.cloud.order.delivery.service.DeliveryEvidenceBindingService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 恢复订单已提交但尚未完成 UPMS 确认的配送凭证绑定。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEvidenceBindingRecoveryJob {

	private static final int BATCH_SIZE = 100;

	private final OrderDeliveryEvidenceMapper evidenceMapper;

	private final OrderDeliveryTaskLogMapper logMapper;

	private final DeliveryEvidenceBindingService bindingService;

	@XxlJob("deliveryEvidenceBindingRecoveryJob")
	public void recoverPendingBindings() {
		List<OrderDeliveryEvidence> pending = evidenceMapper.selectPendingBindings(BATCH_SIZE);
		if (pending == null || pending.isEmpty()) {
			return;
		}
		Set<String> processedTasks = new HashSet<>();
		for (OrderDeliveryEvidence evidence : pending) {
			String taskKey = evidence.getTenantId() + ":" + evidence.getTaskId();
			if (!processedTasks.add(taskKey)) {
				continue;
			}
			recoverTask(evidence.getTenantId(), evidence.getTaskId());
		}
	}

	private void recoverTask(String tenantId, String taskId) {
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			OrderDeliveryTaskLog actionLog = logMapper.selectLatestEvidenceAction(tenantId, taskId);
			if (actionLog == null || !StringUtils.hasText(actionLog.getRequestId())) {
				log.warn("配送凭证绑定恢复缺少操作日志，tenantId={}, taskId={}", tenantId, taskId);
				return;
			}
			bindingService.confirmBinding(tenantId, taskId + ":" + actionLog.getRequestId(), taskId);
		}
		catch (RuntimeException exception) {
			log.error("配送凭证绑定恢复失败，tenantId={}, taskId={}", tenantId, taskId, exception);
		}
		finally {
			if (StringUtils.hasText(previousTenantId)) {
				ArynTenantContextHolder.setTenantId(previousTenantId);
			}
			else {
				ArynTenantContextHolder.removeTenantId();
			}
		}
	}

}
