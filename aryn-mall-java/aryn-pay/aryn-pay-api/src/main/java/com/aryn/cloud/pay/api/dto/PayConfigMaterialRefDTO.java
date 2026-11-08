
package com.aryn.cloud.pay.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "支付身份版本材料引用")
public class PayConfigMaterialRefDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotBlank(message = "材料用途不能为空")
	private String purpose;

	@NotBlank(message = "材料ID不能为空")
	private String materialId;

}
