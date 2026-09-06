package com.aryn.cloud.promotion.api.vo;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.promotion.api.entity.PageDesignRelease;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "页面装修发布申请")
public class PageDesignReleaseVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "页面ID")
	private String pageDesignId;

	@Schema(description = "页面内申请序号")
	private Integer releaseNo;

	@Schema(description = "发布策略：0.立即；1.定时；2.灰度；")
	private String releaseStrategy;

	@Schema(description = "申请状态：0.待审核；1.已发布；2.已拒绝；3.已取消；")
	private String releaseStatus;

	@Schema(description = "申请时草稿修订号")
	private Long draftRevision;

	@Schema(description = "装修协议版本")
	private Integer schemaVersion;

	@Schema(description = "申请时页面名称")
	private String pageName;

	@Schema(description = "申请内容快照")
	private JSONObject pageContent;

	@Schema(description = "发布备注")
	private String publishRemark;

	@Schema(description = "审批意见")
	private String auditRemark;

	@Schema(description = "提交人")
	private String submitBy;

	@Schema(description = "提交时间")
	private LocalDateTime submitAt;

	@Schema(description = "审批人")
	private String auditBy;

	@Schema(description = "审批时间")
	private LocalDateTime auditAt;

	@Schema(description = "审批通过后生成的发布版本ID")
	private String releaseVersionId;

	public static PageDesignReleaseVO from(PageDesignRelease release) {
		PageDesignReleaseVO vo = new PageDesignReleaseVO();
		vo.setId(release.getId());
		vo.setPageDesignId(release.getPageDesignId());
		vo.setReleaseNo(release.getReleaseNo());
		vo.setReleaseStrategy(release.getReleaseStrategy());
		vo.setReleaseStatus(release.getReleaseStatus());
		vo.setDraftRevision(release.getDraftRevision());
		vo.setSchemaVersion(release.getSchemaVersion());
		vo.setPageName(release.getPageName());
		vo.setPageContent(parseContent(release.getPageContent()));
		vo.setPublishRemark(release.getPublishRemark());
		vo.setAuditRemark(release.getAuditRemark());
		vo.setSubmitBy(release.getSubmitBy());
		vo.setSubmitAt(release.getSubmitAt());
		vo.setAuditBy(release.getAuditBy());
		vo.setAuditAt(release.getAuditAt());
		vo.setReleaseVersionId(release.getReleaseVersionId());
		return vo;
	}

	private static JSONObject parseContent(String pageContent) {
		try {
			return JSON.parseObject(pageContent);
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

}
