package com.aryn.cloud.promotion.api.dto;
import lombok.Data;
@Data
public class CampaignGenerationCommandDTO {
	private Long expectedRevision;
	private String requestId;
}
