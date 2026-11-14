package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理端添加/调整采购主体成员 DTO。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Data
@Schema(description = "采购主体成员DTO")
public class BuyerOrgMemberDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotBlank(message = "用户ID不能为空")
	@Schema(description = "商城用户ID")
	private String userId;

	@Schema(description = "角色：1厂长/管理员 2采购员 3业务员 4查看者")
	private String memberRole;

	@Schema(description = "是否可加购/改数/建车：1是 0否；空则按角色默认")
	private String canEdit;

	@Schema(description = "是否可确认提交：1是 0否；空则按角色默认")
	private String canConfirm;

	@Schema(description = "备注")
	private String remark;

}
