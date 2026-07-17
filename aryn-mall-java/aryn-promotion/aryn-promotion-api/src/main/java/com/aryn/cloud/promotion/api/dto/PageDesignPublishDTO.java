package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "页面装修发布请求")
public class PageDesignPublishDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String id;

	@NotNull(message = "草稿修订号不能为空")
	private Long draftRevision;

	private String publishRemark;

}
