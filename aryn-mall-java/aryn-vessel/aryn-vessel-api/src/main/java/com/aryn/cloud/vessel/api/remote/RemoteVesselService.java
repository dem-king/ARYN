package com.aryn.cloud.vessel.api.remote;

import com.aryn.cloud.vessel.api.dto.VesselContextDTO;

/**
 * 船舶域远程服务接口。
 *
 * <p>供订单等其他 biz 模块通过 Dubbo（Cloud）或本地 Bean（Boot）调用，
 * 接口定义只落在 aryn-vessel-api，实现由 aryn-vessel-biz 提供。
 *
 * @author aryn
 * @since 2026/9/11
 */
public interface RemoteVesselService {

	/**
	 * 校验用户是否为指定船舶的在船成员。
	 * @param tenantId 租户ID
	 * @param vesselId 船舶ID
	 * @param userId 商城用户ID
	 * @return true 表示是在船成员
	 */
	boolean isVesselMember(String tenantId, String vesselId, String userId);

	/**
	 * 凭分享加入时自动补建船舶成员关系（幂等：已是成员则直接返回）。
	 *
	 * <p>场景：船员通过微信群分享卡片首次进入商城，此前没有船舶成员关系。
	 * 补建后即获得该船船员身份，后续可直接参与船供采购与内部配送。
	 *
	 * @param tenantId 租户ID
	 * @param vesselId 船舶ID
	 * @param userId 商城用户ID
	 * @return true 表示已成为成员（新建或原本就是）
	 */
	boolean bindMemberByShare(String tenantId, String vesselId, String userId);

	/**
	 * 查询靠港计划的配送上下文（含船舶名称、港口、泊位和时间窗）。
	 * @param tenantId 租户ID
	 * @param vesselCallId 靠港计划ID
	 * @return 上下文；靠港计划不存在或已过期返回 null
	 */
	VesselContextDTO getVesselCallContext(String tenantId, String vesselCallId);

}
