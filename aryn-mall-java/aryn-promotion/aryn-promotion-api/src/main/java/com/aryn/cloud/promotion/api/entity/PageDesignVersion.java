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

@Data
@Schema(description = "页面装修发布版本")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("page_design_version")
public class PageDesignVersion extends Model<PageDesignVersion> {

	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private String id;

	@Schema(description = "页面ID")
	private String pageDesignId;

	@Schema(description = "版本号")
	private Integer versionNo;

	@Schema(description = "装修协议版本")
	private Integer schemaVersion;

	@Schema(description = "发布时页面名称")
	private String pageName;

	@Schema(description = "页面类型：0.微页面；1.首页；")
	private String pageType;

	@Schema(description = "发布页面内容")
	private String pageContent;

	@Schema(description = "发布备注")
	private String publishRemark;

	@Schema(description = "发布人")
	private String publishBy;

	@Schema(description = "发布时间")
	private LocalDateTime publishedAt;

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
