package com.aryn.cloud.order.service;

/**
 * 共享购物车邀请成员的站内通知。
 *
 * <p>被邀请人此前对「被拉入购物车」完全无感：手填 ID 邀请直接落库、不校验、
 * 不通知，对方只能下次打开小程序从「我参与的共享购物车」里被动发现。
 * 邀请保持「直接拉入」语义（组织内强信任，与分享卡片自动加入一致），但必须
 * 让对方知道这件事——本服务补齐感知环节。
 *
 * <p>收件人是 C 端商城用户（MALL_USER），发送走消息域 MQ 命令；幂等由站内信
 * (source_type, source_key) 唯一键兜底，eventId 含购物车与用户主键。本服务不抛
 * 异常：发送失败只记日志，不影响邀请本身。
 *
 * @author aryn
 * @since 2026/11/1
 */
public interface SharedCartInviteNotifyService {

	/**
	 * 通知被邀请人已加入共享购物车。
	 *
	 * @param tenantId 租户ID
	 * @param cartId 购物车ID
	 * @param shoreCart 是否岸上企业车（决定文案口径）
	 * @param vesselName 船舶名称（岸上车传 null；船供车查不到时也允许 null）
	 * @param memberUserId 被邀请人商城用户ID
	 * @param memberNickname 被邀请人昵称（收件人展示名，可为 null）
	 */
	void notifyInvited(String tenantId, String cartId, boolean shoreCart, String vesselName, String memberUserId,
			String memberNickname);

}
