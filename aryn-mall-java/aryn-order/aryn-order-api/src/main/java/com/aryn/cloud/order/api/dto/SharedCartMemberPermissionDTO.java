package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 共享购物车成员明细权限设置请求（管理端）。
 *
 * <p>对应 {@code shared_cart_member.can_edit}：收回后该成员只能查看清单，
 * 不能再加购/改数量/移除自己的明细，也不能走 Excel 导入与历史复用给自己添行
 * （服务端 {@code SharedCartServiceImpl#requireCanEdit} 统一校验）。
 *
 * @author aryn
 * @since 2026/10/9
 */
@Data
@Schema(description = "共享购物车成员明细权限设置请求")
public class SharedCartMemberPermissionDTO {

	@Schema(description = "是否可维护自己的明细：1是 0否")
	@NotBlank(message = "权限标记不能为空")
	@Pattern(regexp = "[01]", message = "权限标记只能是 0 或 1")
	private String canEdit;

}
