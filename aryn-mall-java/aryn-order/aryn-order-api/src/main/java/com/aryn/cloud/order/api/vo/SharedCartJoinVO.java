package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 凭分享令牌加入共享购物车的结果。
 *
 * <p>加入成功不只是「拿到一张购物车的 ID」：服务端会顺手补建该船的船舶成员关系
 * （分享链接是 {@code vessel_member} 的写入通道之一，落库来源为 {@code SHARE_CART}）。
 * 此前接口只回传一个裸 cartId，前端无法告知用户「你同时成为了该船船员」——用户
 * 对成员关系的变化完全无感，也就无从判断是否需要回收。本 VO 只补齐这条提示所需
 * 的最小信息，**不回显 {@code share_token} 等敏感字段**。
 *
 * <p>语义要点：
 * <ul>
 * <li>{@code vesselMemberBound} 表示**本次**是否真的补建了成员关系。原本就在船上
 * （isVesselMember=true，跳过补建）或已是该车成员而幂等返回时为 false，避免对
 * 老船员重复播报「你已加入本船」。</li>
 * <li>补建失败按 fail-open 处理：加入本身仍成功，仅 {@code vesselMemberBound=false}，
 * 船舶成员校验在加购/下单环节再兜底。</li>
 * </ul>
 *
 * @author aryn
 * @since 2026/10/19
 */
@Data
@Schema(description = "凭分享令牌加入共享购物车的结果")
public class SharedCartJoinVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "加入后的购物车ID（前端据此跳转详情）")
	private String cartId;

	@Schema(description = "船舶名称（加入提示用；船舶域不可用或查不到时为 null）")
	private String vesselName;

	@Schema(description = "本次是否自动补建了船舶成员关系（false=原本已是船员/补建失败；加入本身仍成功）")
	private Boolean vesselMemberBound;

}
