package com.aryn.cloud.promotion.api.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/** 活动包本地聚合根；主活动仍是既有领域草稿，不重建一套计价或发布协议。 */
@Data
@TableName("campaign_draft")
public class CampaignDraft {
	@TableId(type=IdType.ASSIGN_ID) private String id;
	private String tenantId;
	private String actorId;
	private Long revision;
	private String title;
	private String definitionJson;
	private String status;
	private String mainPolicyId;
	private String mainPolicyDigest;
	private String mainActivityId;
	private Long mainActivityRevision;
	private String pageId;
	private Long pageRevision;
	private String pageDigest;
	private String simulationId;
	private Long simulationRevision;
	private Integer generationVersion;
	private String generationReason;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
	@TableLogic private String delFlag;
}
