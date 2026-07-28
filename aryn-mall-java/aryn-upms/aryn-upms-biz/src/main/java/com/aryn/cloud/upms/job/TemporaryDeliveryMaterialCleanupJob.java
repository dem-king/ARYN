package com.aryn.cloud.upms.job;

import com.aryn.cloud.upms.api.remote.RemoteMaterialAccessService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 清理超时未绑定的配送凭证素材预占。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemporaryDeliveryMaterialCleanupJob {

	private final RemoteMaterialAccessService materialAccessService;

	@XxlJob("temporaryDeliveryMaterialCleanupJob")
	public void cleanupExpiredReservations() {
		int released = materialAccessService.releaseExpiredReservations();
		log.info("商城配送临时素材预占清理完成，释放数量：{}", released);
	}

}
