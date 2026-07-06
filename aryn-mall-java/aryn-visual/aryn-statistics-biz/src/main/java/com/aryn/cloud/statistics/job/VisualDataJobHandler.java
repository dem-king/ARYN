package com.aryn.cloud.statistics.job;

import com.aryn.cloud.statistics.service.impl.VisualDataServiceImpl;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 大屏数据缓存预热定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisualDataJobHandler {

	private final VisualDataServiceImpl visualDataService;

	/**
	 * 大屏数据缓存预热 (每5分钟执行一次)
	 */
	@XxlJob("visualDataPreloadJobHandler")
	public void visualDataPreloadJobHandler() {
		XxlJobHelper.log("开始预热大屏缓存数据");
		visualDataService.preloadAllCache();
		XxlJobHelper.log("大屏缓存数据预热完成");
	}

}