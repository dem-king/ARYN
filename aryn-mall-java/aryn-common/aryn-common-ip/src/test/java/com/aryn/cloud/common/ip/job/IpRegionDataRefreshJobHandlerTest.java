package com.aryn.cloud.common.ip.job;

import com.aryn.cloud.common.ip.updater.IpRegionDataUpdater;
import com.aryn.cloud.common.ip.updater.IpRegionRefreshResult;
import com.xxl.job.core.context.XxlJobContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据刷新任务：参数解析、成功/失败回写调度结果。
 */
class IpRegionDataRefreshJobHandlerTest {

	@AfterEach
	void tearDown() {
		XxlJobContext.setXxlJobContext(null);
	}

	@Test
	void shouldPassForceFlagAndMarkSuccess() {
		IpRegionDataUpdater updater = mock(IpRegionDataUpdater.class);
		when(updater.refresh(true)).thenReturn(new IpRegionRefreshResult(IpRegionRefreshResult.Status.UPDATED,
				"xdb 已更新", 11, "\"etag\"", 1L, 2L));
		mockJobContext("force");

		new IpRegionDataRefreshJobHandler(updater).refresh();

		verify(updater).refresh(true);
		assertThat(XxlJobContext.getXxlJobContext().getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
	}

	@Test
	void shouldMarkFailWhenUpdateFailed() {
		IpRegionDataUpdater updater = mock(IpRegionDataUpdater.class);
		when(updater.refresh(anyBoolean())).thenReturn(new IpRegionRefreshResult(
				IpRegionRefreshResult.Status.FAILED, "更新失败：network down", 0, null, 0L, 1L));
		mockJobContext(null);

		new IpRegionDataRefreshJobHandler(updater).refresh();

		assertThat(XxlJobContext.getXxlJobContext().getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
	}

	@Test
	void shouldTreatUpToDateAsSuccess() {
		IpRegionDataUpdater updater = mock(IpRegionDataUpdater.class);
		when(updater.refresh(false)).thenReturn(new IpRegionRefreshResult(
				IpRegionRefreshResult.Status.UP_TO_DATE, "远端未变化", 0, "\"etag\"", 1L, 1L));
		mockJobContext("");

		new IpRegionDataRefreshJobHandler(updater).refresh();

		assertThat(XxlJobContext.getXxlJobContext().getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
	}

	@Test
	void shouldParseForceParam() {
		assertThat(IpRegionDataRefreshJobHandler.isForce("force")).isTrue();
		assertThat(IpRegionDataRefreshJobHandler.isForce(" TRUE ")).isTrue();
		assertThat(IpRegionDataRefreshJobHandler.isForce("1")).isTrue();
		assertThat(IpRegionDataRefreshJobHandler.isForce(null)).isFalse();
		assertThat(IpRegionDataRefreshJobHandler.isForce("")).isFalse();
		assertThat(IpRegionDataRefreshJobHandler.isForce("daily")).isFalse();
	}

	private static void mockJobContext(String jobParam) {
		XxlJobContext.setXxlJobContext(new XxlJobContext(1L, jobParam, "ip-region-refresh.log", 0, 1));
	}

}
