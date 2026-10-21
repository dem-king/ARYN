package com.aryn.cloud.vessel.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 船舶成员视图（C 端成员列表用）。
 *
 * <p>刻意**不返回** sysUserId / 内部工号等 B 端标识，只暴露展示所需字段
 * （与配送员绑定商城账号的 VO 约定一致）。
 *
 * @author aryn
 * @since 2026/9/19
 */
@Data
@Schema(description = "船舶成员VO")
public class VesselMemberVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "成员关系ID")
	private String id;

	@Schema(description = "商城用户ID（用于判断「是不是我」）")
	private String userId;

	@Schema(description = "昵称")
	private String nickname;

	@Schema(description = "手机号（脱敏展示）")
	private String phone;

	@Schema(description = "成员角色：1发起人 2普通船员 3采购确认人 4业务员")
	private String memberRole;

	@Schema(description = "在船状态：1在船 0离船")
	private String status;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "加入时间")
	private LocalDateTime joinTime;

	/**
	 * 绑定来源。管理端成员列表据此显示来源标签，运营可以按来源识别并回收低信任关系
	 * （尤其是 SHARE_CART：任何拿到分享链接的人都会被自动补建）。
	 */
	@Schema(description = "绑定来源：APPLY_AUDIT/APPLY_AUDIT_MISMATCH/INVITE_CODE/MEMBER_ADD/ADMIN_ADD/SHARE_CART/MANUAL")
	private String bindSource;

	@Schema(description = "绑定来源中文名")
	private String bindSourceLabel;

	@Schema(description = "绑定补充原因")
	private String bindReason;

}
