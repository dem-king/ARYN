
package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 页面设计
 *
 * @author 雨滴kian
 * @date 2022/12/07
 */
@Data
@Schema(description = "页面设计")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "page_design")
public class PageDesign extends Model<PageDesign> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "页面名称")
	private String pageName;

	@Schema(description = "页面内容")
	private String pageContent;

	@Schema(description = "草稿修订号")
	private Long draftRevision;

	@Schema(description = "装修协议版本")
	private Integer schemaVersion;

	@Schema(description = "当前发布版本ID")
	private String publishedVersionId;

	@Schema(description = "发布状态：0.未发布；1.已发布；")
	private String publishedStatus;

	@Schema(description = "发布时间")
	private LocalDateTime publishedAt;

	@Schema(description = "旧版装修内容备份")
	private String legacyContentBackup;

	@Schema(description = "页面类型：0.微页面；1.首页；")
	private String pageType;

	@Schema(description = "状态：0.正常；1.停用；")
	private String status;

	@Schema(description = "首页页面：0.否；1.是；")
	private String homeStatus;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

	@Schema(description = "租户id")
	private String tenantId;

}
