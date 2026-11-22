package com.aryn.cloud.promotion.api.dto;
import lombok.Data;
import java.io.Serializable;
@Data
public class CampaignPageCommandDTO implements Serializable {
	private Long expectedRevision;
	private Long expectedPageRevision;
	private String simulationId;
}
