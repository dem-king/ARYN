package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 采购主体成员视图。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Data
@Schema(description = "采购主体成员VO")
public class BuyerOrgMemberVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "成员行ID")
	private String id;

	@Schema(description = "采购主体ID")
	private String buyerOrgId;

	@Schema(description = "商城用户ID")
	private String userId;

	@Schema(description = "昵称")
	private String nickname;

	@Schema(description = "头像")
	private String avatarUrl;

	@Schema(description = "脱敏手机号")
	private String phoneMasked;

	@Schema(description = "角色：1厂长/管理员 2采购员 3业务员 4查看者")
	private String memberRole;

	@Schema(description = "1可加购/改数/建车")
	private String canEdit;

	@Schema(description = "1可确认提交")
	private String canConfirm;

	@Schema(description = "状态：1正常 0已移除")
	private String status;

	@Schema(description = "绑定来源")
	private String bindSource;

	@Schema(description = "加入时间")
	private LocalDateTime joinTime;

	@Schema(description = "退出/移除时间")
	private LocalDateTime leaveTime;

}
