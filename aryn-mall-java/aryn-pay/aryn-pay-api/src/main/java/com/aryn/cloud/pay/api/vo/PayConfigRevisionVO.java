
package com.aryn.cloud.pay.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付身份版本安全读取模型。
 */
@Data
@Schema(description = "支付身份版本")
public class PayConfigRevisionVO {

	private String id;

	private String configId;

	private Integer revisionNo;

	private String channel;

	private String terminalType;

	private String appId;

	private String mchId;

	private String environment;

	private String wxPublicKeyId;

	private String buyerIdentityMode;

	private String checkStatus;

	private String checkSummary;

	private LocalDateTime createTime;

	private List<PayConfigMaterialVO> materials;

}
