package com.aryn.cloud.promotion.api.vo;
import com.aryn.cloud.promotion.api.dto.CampaignDraftSaveDTO;
import lombok.Data;
import java.io.Serializable;
import java.util.List;
/** 不返回后台AI配置凭证，也不把DRAFT展示为已发布。 */
@Data
public class CampaignDraftVO implements Serializable {
	private String id;
	private Long revision;
	private String status;
	private CampaignDraftSaveDTO definition;
	private List<String> missingFields;
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
}
