package com.aryn.cloud.promotion.api.dto;

import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "页面装修草稿保存请求")
public class PageDesignDraftDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "页面ID")
	private String id;

	@Schema(description = "页面名称")
	private String pageName;

	@NotNull(message = "页面内容不能为空")
	@Schema(description = "v2装修内容")
	private JSONObject pageContent;

	@NotNull(message = "装修协议版本不能为空")
	@Schema(description = "装修协议版本")
	private Integer schemaVersion;

	@NotNull(message = "草稿修订号不能为空")
	@Schema(description = "客户端当前草稿修订号")
	private Long draftRevision;

}
