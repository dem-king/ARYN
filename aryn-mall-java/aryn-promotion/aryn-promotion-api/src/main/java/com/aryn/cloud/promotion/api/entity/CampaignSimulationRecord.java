package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 不可变模拟凭据，唯一写入副作用；不能代表已占用预算或库存。 */
@Data
@TableName("campaign_simulation")
public class CampaignSimulationRecord {
	@TableId(type = IdType.ASSIGN_ID)
	private String id;
	private String tenantId;
	private String actorId;
	private String draftActivityId;
	private Long draftRevision;
	private String requestJson;
	private String draftDigest;
	private String inputDigest;
	private String factDigest;
	private String resultJson;
	private LocalDateTime expiresAt;
	private LocalDateTime createTime;
	@TableLogic
	private String delFlag;
}
