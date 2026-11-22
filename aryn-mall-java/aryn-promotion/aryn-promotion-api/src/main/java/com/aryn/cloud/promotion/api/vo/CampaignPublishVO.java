package com.aryn.cloud.promotion.api.vo;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data
public class CampaignPublishVO {
 private String id;
 private String draftId;
 private String requestId;
 private String status;
 private List<String> steps;
 private String mainActivityId;
 private String mainPolicyId;
 private String couponId;
 private String pageId;
 private String pageVersionId;
 private String failureCode;
 private LocalDateTime updateTime;
}
