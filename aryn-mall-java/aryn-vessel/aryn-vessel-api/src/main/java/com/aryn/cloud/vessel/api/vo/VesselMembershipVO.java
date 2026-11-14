package com.aryn.cloud.vessel.api.vo;

import com.aryn.cloud.vessel.api.entity.VesselInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * C 端「我的船舶」成员视图。
 *
 * <p>继承 {@link VesselInfo} 保持原有船舶字段结构不变，只追加当前用户在该船的
 * 成员角色与操作权限，老客户端可继续按原字段解析。
 *
 * @author aryn
 * @since 2026/11/13
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "我的船舶（含成员权限）")
public class VesselMembershipVO extends VesselInfo {

	@Schema(description = "成员角色：1发起人 2普通船员 3采购确认人 4业务员")
	private String memberRole;

	@Schema(description = "是否可维护采购清单")
	private Boolean canEdit;

	@Schema(description = "是否可确认提交船供订单")
	private Boolean canConfirm;

}
