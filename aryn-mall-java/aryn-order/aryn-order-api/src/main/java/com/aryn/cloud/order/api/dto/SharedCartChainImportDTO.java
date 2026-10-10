package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 共享购物车接龙粘贴导入 DTO（确认人/发起人操作）。
 *
 * @author aryn
 * @since 2026/10/11
 */
@Data
@Schema(description = "共享购物车接龙粘贴导入DTO")
public class SharedCartChainImportDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "接龙原文（整段粘贴）")
	@NotBlank(message = "接龙文本不能为空")
	@Size(max = 50000, message = "接龙文本过长")
	private String text;

}
