package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 专区系统活动同步结果 / 同步状态。
 *
 * <p>同时服务两类调用：
 * <ul>
 *   <li>{@code RemoteMallZoneSyncService#syncTenant} 返回本次同步结果
 *       （{@code success / message / finishedAt / zones}）；</li>
 *   <li>管理端 {@code GET /zone/sync-status}、{@code POST /zone/sync} 返回
 *       {@code zones} 与 {@code checkedAt}。</li>
 * </ul>
 *
 * <p>同步失败时 {@code success=false}，调用方（商品批量打标响应）据此写入
 * {@code sync.status=FAILED}，不得再宣称「价格已同步」。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "专区同步结果/状态")
public class MallZoneSyncResultVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "本次同步是否整体成功")
	private Boolean success;

	@Schema(description = "结果说明/失败原因")
	private String message;

	@Schema(description = "本次同步完成时间")
	private LocalDateTime finishedAt;

	@Schema(description = "状态查询时间（sync-status 返回）")
	private LocalDateTime checkedAt;

	@Schema(description = "分专区同步状态")
	private List<MallZoneSyncStateVO> zones = new ArrayList<>();

	public static MallZoneSyncResultVO success(String message, LocalDateTime finishedAt,
			List<MallZoneSyncStateVO> zones) {
		MallZoneSyncResultVO vo = new MallZoneSyncResultVO();
		vo.setSuccess(true);
		vo.setMessage(message);
		vo.setFinishedAt(finishedAt);
		vo.setZones(zones == null ? new ArrayList<>() : zones);
		return vo;
	}

	public static MallZoneSyncResultVO failed(String message, LocalDateTime finishedAt,
			List<MallZoneSyncStateVO> zones) {
		MallZoneSyncResultVO vo = new MallZoneSyncResultVO();
		vo.setSuccess(false);
		vo.setMessage(message);
		vo.setFinishedAt(finishedAt);
		vo.setZones(zones == null ? new ArrayList<>() : zones);
		return vo;
	}

}
