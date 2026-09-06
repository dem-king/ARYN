package com.aryn.cloud.promotion.api.vo;

import com.aryn.cloud.promotion.api.entity.PageDesignAuditLog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "页面装修审计日志")
public class PageDesignAuditLogVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "页面ID")
	private String pageDesignId;

	@Schema(description = "关联发布申请ID")
	private String releaseId;

	@Schema(description = "操作类型")
	private String action;

	@Schema(description = "操作人")
	private String operator;

	@Schema(description = "操作者IP")
	private String operatorIp;

	@Schema(description = "操作前发布版本ID")
	private String beforeVersionId;

	@Schema(description = "操作后发布版本ID")
	private String afterVersionId;

	@Schema(description = "操作前草稿修订号")
	private Long beforeRevision;

	@Schema(description = "操作后草稿修订号")
	private Long afterRevision;

	@Schema(description = "操作结果：0.成功；1.失败；")
	private String result;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "操作时间")
	private LocalDateTime createTime;

	public static PageDesignAuditLogVO from(PageDesignAuditLog log) {
		PageDesignAuditLogVO vo = new PageDesignAuditLogVO();
		vo.setId(log.getId());
		vo.setPageDesignId(log.getPageDesignId());
		vo.setReleaseId(log.getReleaseId());
		vo.setAction(log.getAction());
		vo.setOperator(log.getOperator());
		vo.setOperatorIp(log.getOperatorIp());
		vo.setBeforeVersionId(log.getBeforeVersionId());
		vo.setAfterVersionId(log.getAfterVersionId());
		vo.setBeforeRevision(log.getBeforeRevision());
		vo.setAfterRevision(log.getAfterRevision());
		vo.setResult(log.getResult());
		vo.setRemark(log.getRemark());
		vo.setCreateTime(log.getCreateTime());
		return vo;
	}

}
