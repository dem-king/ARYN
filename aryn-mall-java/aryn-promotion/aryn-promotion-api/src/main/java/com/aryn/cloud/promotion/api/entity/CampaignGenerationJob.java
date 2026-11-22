package com.aryn.cloud.promotion.api.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/** 持久异步生成；租约令牌和baseRevision共同拒绝迟到worker覆盖人工修改。 */
@Data
@TableName("campaign_generation_job")
public class CampaignGenerationJob {
	@TableId(type=IdType.ASSIGN_ID) private String id;
	private String tenantId;
	private String draftId;
	private String actorId;
	private String requestId;
	private Long baseRevision;
	private String status;
	private String inputJson;
	private String leaseToken;
	private LocalDateTime leaseUntil;
	private Integer attempt;
	private LocalDateTime nextRetryAt;
	private String configVersion;
	private String providerId;
	private String model;
	private String promptVersion;
	private String inputDigest;
	private String outputJson;
	private String errorCode;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
	@TableLogic private String delFlag;
}
