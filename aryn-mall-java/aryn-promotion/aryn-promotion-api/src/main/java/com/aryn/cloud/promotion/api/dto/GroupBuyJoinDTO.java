package com.aryn.cloud.promotion.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GroupBuyJoinDTO {

	@NotBlank(message = "拼团活动ID不能为空")
	private String activityId;

	private String recordId;
}
