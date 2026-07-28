package com.aryn.cloud.upms.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 配送凭证素材的受控访问描述。 */
@Data
public class MaterialAccessVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String materialId;
	private String accessUrl;
	private LocalDateTime expiresAt;
	private String bindingStatus;

}
