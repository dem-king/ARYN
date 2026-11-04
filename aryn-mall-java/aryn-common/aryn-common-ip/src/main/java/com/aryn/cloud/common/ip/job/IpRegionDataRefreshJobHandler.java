package com.aryn.cloud.common.ip.job;

import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.ip.updater.IpRegionDataUpdater;
import com.aryn.cloud.common.ip.updater.IpRegionRefreshResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * ip2region xdb 数据刷新任务。
 *
 * <p>注册方式：XXL-JOB 调度中心「执行器任务」新增任务，JobHandler 填
 * {@code ipRegionDataRefreshJobHandler}；建议 CRON 每日低峰执行一次，
 * 多实例部署时路由策略选「分片广播」（各实例都会触发，但文件锁保证只有一个真正下载，
 * 其余实例拿不到锁直接跳过）。任务参数可传 {@code force} 跳过 ETag 强制重新下载。</p>
 */
@Slf4j
public class IpRegionDataRefreshJobHandler {

	private final IpRegionDataUpdater updater;

	public IpRegionDataRefreshJobHandler(IpRegionDataUpdater updater) {
		this.updater = updater;
	}

	@XxlJob("ipRegionDataRefreshJobHandler")
	public void refresh() {
		boolean force = isForce(XxlJobHelper.getJobParam());
		XxlJobHelper.log("ip2region xdb 刷新开始，force={}", force);
		IpRegionRefreshResult result = updater.refresh(force);
		log.info("ip2region xdb 刷新结果：{} - {}", result.status(), result.message());
		XxlJobHelper.log("ip2region xdb 刷新结果：{} - {}", result.status(), result.message());
		if (result.failed()) {
			XxlJobHelper.handleFail(result.message());
			return;
		}
		XxlJobHelper.handleSuccess(result.message());
	}

	/**
	 * 解析任务参数：force / true / 1 视为强制刷新。
	 * @param jobParam 调度中心配置的任务参数
	 * @return 是否强制
	 */
	static boolean isForce(String jobParam) {
		if (StrUtil.isBlank(jobParam)) {
			return false;
		}
		String normalized = jobParam.trim().toLowerCase(Locale.ROOT);
		return "force".equals(normalized) || "true".equals(normalized) || "1".equals(normalized);
	}

}
