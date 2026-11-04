package com.aryn.cloud.promotion.api.remote;

import com.aryn.cloud.promotion.api.vo.MallZoneSyncResultVO;

/**
 * 会员/出清专区系统活动同步接口。
 *
 * <p>商品标签保存后由 product 域调用，把最新打标结果全量重算到系统托管活动
 * {@code SYS_MEMBER_ZONE}（type=10）与 {@code SYS_CLEARANCE_ZONE}（type=11）；
 * 每日兜底 Job 同样调用本接口处理到期退出与漏同步。
 *
 * <p>同步必须幂等：按 SKU 全量覆盖 rules，系统活动按租户唯一且 id 稳定，
 * 禁止删除后重建（在途锁与快照依赖 activityId 不变）。
 *
 * <p>失败语义（G13）：快照查询失败或单专区重算失败时不得用空快照覆盖已有 rules，
 * 也不得只记日志后返回成功；本方法返回 {@link MallZoneSyncResultVO}，
 * {@code success=false} 时调用方必须把同步失败暴露给前端，禁止宣称价格已同步。
 *
 * @author aryn
 * @since 2026/11/03
 */
public interface RemoteMallZoneSyncService {

	/**
	 * 重算指定租户的两个系统托管活动，并记录本次同步状态。
	 *
	 * @param tenantId 租户 ID
	 * @return 同步结果（非空）；失败时 {@code success=false} 且 {@code message} 说明原因
	 */
	MallZoneSyncResultVO syncTenant(String tenantId);

}
