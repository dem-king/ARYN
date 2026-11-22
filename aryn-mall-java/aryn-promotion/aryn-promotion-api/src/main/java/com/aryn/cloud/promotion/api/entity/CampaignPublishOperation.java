package com.aryn.cloud.promotion.api.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("campaign_publish_operation")
public class CampaignPublishOperation {
 @TableId(type=IdType.ASSIGN_ID) private String id;
 private String tenantId;
 private String actorId;
 private String draftId;
 private String requestId;
 private String commandJson;
 private String commandDigest;
 private String status;
 private String stepsJson;
 private String mainActivityId;
 private String mainPolicyId;
 private String couponId;
 private String pageId;
 private String pageVersionId;
 private String failureCode;
 private LocalDateTime createTime;
 private LocalDateTime updateTime;
 @TableLogic private String delFlag;
}
