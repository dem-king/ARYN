package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 拼团成员轻量出参（列表/详情头像环展示用）。
 *
 * <p>只承载公开展示字段：用户 ID、昵称、头像与参团状态；
 * 不包含手机号、密码等敏感字段，也不直接复用 {@code GroupBuyMember} 实体。
 */
@Data
@Schema(description = "拼团成员出参（轻量，昵称/头像展示）")
public class GroupBuyMemberVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "参团用户ID")
	private String userId;

	@Schema(description = "昵称（远程用户服务查不到时回落「拼友」）")
	private String nickname;

	@Schema(description = "头像URL（远程用户服务查不到时为空，前端走默认灰圆兜底）")
	private String avatarUrl;

	@Schema(description = "是否团长:0否,1是")
	private String isLeader;

	@Schema(description = "参团状态:0待付款,1已付款,2已取消")
	private String memberStatus;
}
