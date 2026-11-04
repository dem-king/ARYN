package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 单个专区的同步状态快照（会员 / 出清）。
 *
 * <p>数据来源：{@code mall_zone_setting}（开关、上次同步时间/结果/错误）与
 * {@code promotion_activity} 系统托管活动（activityId、活动状态、rules 内 SKU 数）。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "专区同步状态")
public class MallZoneSyncStateVO implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 上次同步成功。 */
	public static final String LAST_SYNC_SUCCESS = "SUCCESS";

	/** 上次同步失败。 */
	public static final String LAST_SYNC_FAILED = "FAILED";

	/** 尚无同步记录（历史租户或从未同步）。 */
	public static final String LAST_SYNC_UNKNOWN = "UNKNOWN";

	@Schema(description = "专区：member / clearance")
	private String zone;

	@Schema(description = "专区开关是否开启")
	private Boolean enabled;

	@Schema(description = "系统托管活动ID（未创建则为空）")
	private String activityId;

	@Schema(description = "系统托管活动状态：2已发布 3已暂停")
	private String activityStatus;

	@Schema(description = "系统活动 rules 内的 SKU 数（实际参与专区价的规格数）")
	private Integer rulesSkuCount;

	@Schema(description = "上次同步时间")
	private LocalDateTime lastSyncTime;

	@Schema(description = "上次同步结果：SUCCESS / FAILED / UNKNOWN")
	private String lastSyncStatus;

	@Schema(description = "上次同步失败原因")
	private String lastError;

}
