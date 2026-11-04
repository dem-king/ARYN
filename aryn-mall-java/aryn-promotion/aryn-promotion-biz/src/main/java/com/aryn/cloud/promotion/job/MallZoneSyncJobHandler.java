package com.aryn.cloud.promotion.job;

import com.aryn.cloud.promotion.service.impl.MallZoneActivitySyncServiceImpl;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 会员/出清专区兜底同步：处理出清到期退出、打标后同步失败漏单。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MallZoneSyncJobHandler {

	private final MallZoneActivitySyncServiceImpl mallZoneActivitySyncService;

	@XxlJob("mallZoneSyncJobHandler")
	public void mallZoneSyncJobHandler() {
		XxlJobHelper.log("会员/出清专区系统活动兜底同步开始.");
		try {
			mallZoneActivitySyncService.syncAllTenants();
		}
		catch (Exception ex) {
			log.error("会员/出清专区系统活动兜底同步失败", ex);
			XxlJobHelper.log(ex);
			throw ex;
		}
		XxlJobHelper.log("会员/出清专区系统活动兜底同步完成.");
	}

}
