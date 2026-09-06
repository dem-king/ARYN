package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "页面装修灰度发布目标")
public class PageDesignGrayTargetDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotBlank(message = "灰度目标租户不能为空")
	@Schema(description = "灰度目标租户ID")
	private String targetTenantId;

	@Schema(description = "灰度目标终端：all/h5/weapp，默认 all")
	private String terminal;

}
