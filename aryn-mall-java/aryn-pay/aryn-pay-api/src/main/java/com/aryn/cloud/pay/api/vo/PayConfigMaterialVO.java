
package com.aryn.cloud.pay.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付材料安全读取模型。私钥/APIv3 密钥只返回是否已设置，不返回任何掩码或原文。
 */
@Data
@Schema(description = "支付材料")
public class PayConfigMaterialVO {

	private String materialId;

	private String purpose;

	@Schema(description = "明文 SHA-256 指纹")
	private String fingerprint;

	private String contentType;

	private Integer byteSize;

	private String certSerialNo;

	private LocalDateTime certNotBefore;

	private LocalDateTime certNotAfter;

	private LocalDateTime createTime;

	@Schema(description = "是否属于不可回显的敏感材料")
	private Boolean sensitive;

}
