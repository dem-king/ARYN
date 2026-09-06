package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "页面装修发布申请创建请求")
public class PageDesignReleaseCreateDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotNull(message = "草稿修订号不能为空")
	@Schema(description = "客户端当前草稿修订号")
	private Long draftRevision;

	@Schema(description = "发布策略：0.立即；1.定时；2.灰度；默认立即")
	private String releaseStrategy;

	@Schema(description = "发布备注")
	private String publishRemark;

	@Schema(description = "计划发布时间（定时策略）")
	private LocalDateTime planPublishAt;

	@Schema(description = "灰度目标（灰度策略）")
	private List<PageDesignGrayTargetDTO> targets;

}
