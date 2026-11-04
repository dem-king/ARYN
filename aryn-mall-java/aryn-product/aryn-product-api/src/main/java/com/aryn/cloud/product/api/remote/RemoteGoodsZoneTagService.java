package com.aryn.cloud.product.api.remote;

import com.aryn.cloud.product.api.dto.GoodsZoneSkuSnapshotDTO;

import java.util.List;

/**
 * 商品专区标签远程接口：供促销域同步系统托管活动（SYS_MEMBER_ZONE / SYS_CLEARANCE_ZONE）。
 *
 * <p>所有查询以显式 tenantId 锁定租户；调用异常由调用方按「同步失败」降级，
 * 每日兜底 Job 会重试。
 *
 * @author aryn
 * @since 2026/11/03
 */
public interface RemoteGoodsZoneTagService {

	/**
	 * 当前租户已打会员/出清标签且在售的 SKU 快照（售价与生效规则）。
	 *
	 * @param tenantId 租户 ID
	 * @return 快照列表；无标签商品返回空列表
	 */
	List<GoodsZoneSkuSnapshotDTO> listZoneSkus(String tenantId);

}
