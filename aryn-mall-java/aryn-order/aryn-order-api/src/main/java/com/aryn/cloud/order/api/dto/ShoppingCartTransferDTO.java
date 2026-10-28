package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 购物车行转入靠港计划 DTO。
 *
 * <p>旧靠港结束后留在购物车的行无法随新靠港结算（防串船拦截），此前用户只能
 * 删除后重新加购。本 DTO 支撑「转入本次靠港」：把选中行的归属快照刷成当前
 * 靠港，同用户同 SKU 在目标靠港已有行时由服务端合并数量（生成列唯一键
 * uk_shopping_cart_active_row 约束，不能撞）。
 *
 * @author aryn
 * @since 2026/10/24
 */
@Data
@Schema(description = "购物车行转入靠港计划DTO")
public class ShoppingCartTransferDTO {

	@NotBlank(message = "目标靠港计划不能为空")
	@Schema(description = "目标靠港计划ID（当前船舶上下文的靠港）")
	private String callId;

	@NotEmpty(message = "要转入的购物车行不能为空")
	@Schema(description = "要转入的购物车行ID")
	private List<@NotBlank String> rowIds;

}
