
package com.aryn.cloud.pay.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传结果只返回不透明 materialId 与公开元数据。
 */
@Data
@Schema(description = "支付材料上传结果")
public class PayConfigMaterialUploadVO {

	private String materialId;

	private String purpose;

	private String fingerprint;

	private String contentType;

	private Integer byteSize;

	private String certSerialNo;

	private LocalDateTime certNotBefore;

	private LocalDateTime certNotAfter;

	private Boolean sensitive;

	private Boolean keyPairMatched;

}
