package com.aryn.cloud.promotion.api.dto;
import lombok.Data;
import java.util.Set;
@Data
public class CampaignPublishCommandDTO {
 private String requestId;
 private Long expectedRevision;
 private Long expectedPageRevision;
 private String simulationId;
 private String inputDigest;
 private String factDigest;
 private String pageDigest;
 private boolean acceptOverlap;
 private Set<String> acceptedRiskCodes;
}
