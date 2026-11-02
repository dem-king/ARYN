package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 共享购物车「邀请成员」的候选人。
 *
 * <p>候选人来自两个口径：船供车 = 本船在船成员（船舶域检索，姓名/手机号匹配在
 * 原始手机号上完成）；岸上企业车 = 同租户用户（用户域租户内搜索）。
 * 手机号一律服务端脱敏后再出参，前端不做任何明文处理。
 *
 * <p>此前邀请弹窗让发起人手填对方 19 位商城用户 ID：用户无处查到自己的 ID，
 * 后端也不校验其存在性，填错就静默落一条永远无感知的无效成员行——本 VO 是
 * 「选人邀请」改造的出参载体，发起人只做选择题不做填空题。
 *
 * @author aryn
 * @since 2026/11/1
 */
@Data
@Schema(description = "共享购物车成员候选人")
public class SharedCartMemberCandidateVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "商城用户ID（邀请入参）")
	private String userId;

	@Schema(description = "昵称/姓名")
	private String nickname;

	@Schema(description = "头像（岸上企业车候选来自用户域时有值；船供车为 null）")
	private String avatarUrl;

	@Schema(description = "手机号（服务端脱敏，138****5678）")
	private String phone;

	@Schema(description = "是否已是本购物车成员（true 时前端禁用邀请按钮）")
	private Boolean alreadyMember;

}
