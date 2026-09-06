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
 * 页面装修发布申请。
 * <p>
 * 承载发布前校验通过后的内容快照，审批通过后基于快照生成不可变发布版本；
 * 灰度/定时策略字段为后续阶段预留。
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
@Data
@Schema(description = "页面装修发布申请")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("page_design_release")
public class PageDesignRelease extends Model<PageDesignRelease> {

	/**
	 * 发布策略：立即发布。
	 */
	public static final String STRATEGY_IMMEDIATE = "0";

	/**
	 * 发布策略：定时发布（预留）。
	 */
	public static final String STRATEGY_SCHEDULED = "1";

	/**
	 * 发布策略：灰度发布（预留）。
	 */
	public static final String STRATEGY_GRAY = "2";

	/**
	 * 申请状态：待审核。
	 */
	public static final String STATUS_PENDING = "0";

	/**
	 * 申请状态：已发布（审批通过且发布成功）。
	 */
	public static final String STATUS_PUBLISHED = "1";

	/**
	 * 申请状态：已拒绝。
	 */
	public static final String STATUS_REJECTED = "2";

	/**
	 * 申请状态：已取消。
	 */
	public static final String STATUS_CANCELLED = "3";

	/**
	 * 申请状态：待发布（定时策略审批通过后等待到达计划时间）。
	 */
	public static final String STATUS_WAITING = "4";

	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private String id;

	@Schema(description = "页面ID")
	private String pageDesignId;

	@Schema(description = "页面内申请序号，从1递增")
	private Integer releaseNo;

	@Schema(description = "发布策略：0.立即；1.定时（预留）；2.灰度（预留）；")
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
	private String pageContent;

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

	@Schema(description = "失败原因（预留）")
	private String failReason;

	@Schema(description = "计划发布时间（预留）")
	private LocalDateTime planPublishAt;

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
