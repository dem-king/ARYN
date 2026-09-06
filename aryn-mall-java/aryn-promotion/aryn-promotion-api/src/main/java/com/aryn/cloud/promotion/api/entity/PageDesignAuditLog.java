package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 页面装修审计日志（追加写，不提供业务更新）。
 * <p>
 * 记录草稿保存、发布、下线、回滚与发布申请全流程，
 * 保证任何线上版本可定位到租户、操作者与前后版本。
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
@Data
@Schema(description = "页面装修审计日志")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("page_design_audit_log")
public class PageDesignAuditLog extends Model<PageDesignAuditLog> {

	/**
	 * 操作类型：保存草稿。
	 */
	public static final String ACTION_SAVE_DRAFT = "SAVE_DRAFT";

	/**
	 * 操作类型：发布。
	 */
	public static final String ACTION_PUBLISH = "PUBLISH";

	/**
	 * 操作类型：下线。
	 */
	public static final String ACTION_UNPUBLISH = "UNPUBLISH";

	/**
	 * 操作类型：回滚。
	 */
	public static final String ACTION_ROLLBACK = "ROLLBACK";

	/**
	 * 操作类型：提交发布申请。
	 */
	public static final String ACTION_RELEASE_SUBMIT = "RELEASE_SUBMIT";

	/**
	 * 操作类型：发布申请审批通过。
	 */
	public static final String ACTION_RELEASE_APPROVE = "RELEASE_APPROVE";

	/**
	 * 操作类型：发布申请拒绝。
	 */
	public static final String ACTION_RELEASE_REJECT = "RELEASE_REJECT";

	/**
	 * 操作类型：发布申请取消。
	 */
	public static final String ACTION_RELEASE_CANCEL = "RELEASE_CANCEL";

	/**
	 * 操作结果：成功。
	 */
	public static final String RESULT_SUCCESS = "0";

	/**
	 * 操作结果：失败。
	 */
	public static final String RESULT_FAILURE = "1";

	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private String id;

	@Schema(description = "页面ID")
	private String pageDesignId;

	@Schema(description = "关联发布申请ID")
	private String releaseId;

	@Schema(description = "操作类型：SAVE_DRAFT/PUBLISH/UNPUBLISH/ROLLBACK/RELEASE_SUBMIT/RELEASE_APPROVE/RELEASE_REJECT/RELEASE_CANCEL")
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

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

	@Schema(description = "租户ID")
	private String tenantId;

}
