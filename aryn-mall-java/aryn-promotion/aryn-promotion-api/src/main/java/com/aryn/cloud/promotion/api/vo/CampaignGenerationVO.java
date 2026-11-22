package com.aryn.cloud.promotion.api.vo;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
/** HTTP状态不携带内部候选成本、完整输入输出、凭证或租约令牌。 */
@Data
public class CampaignGenerationVO implements Serializable {
	private String id;
	private String draftId;
	private String requestId;
	private Long baseRevision;
	private String status;
	private Integer attempt;
	private LocalDateTime nextRetryAt;
	private String configVersion;
	private String model;
	private String promptVersion;
	private String errorCode;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
}
