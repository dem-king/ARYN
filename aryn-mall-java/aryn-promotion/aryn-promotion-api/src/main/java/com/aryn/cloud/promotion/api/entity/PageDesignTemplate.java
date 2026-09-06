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
@Schema(description = "页面装修模板")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("page_design_template")
public class PageDesignTemplate extends Model<PageDesignTemplate> {

	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private String id;

	@Schema(description = "模板名称")
	private String templateName;

	@Schema(description = "模板类型：0.页面；1.组件组合；")
	private String templateType;

	@Schema(description = "适用页面类型：0.微页面；1.首页；2.通用；")
	private String pageType;

	@Schema(description = "模板内容")
	private String templateContent;

	@Schema(description = "装修协议版本")
	private Integer schemaVersion;

	@Schema(description = "系统模板：0.否；1.是；")
	private String systemFlag;

	@Schema(description = "行业标签（行业模板筛选用，通用为空）")
	private String industryTag;

	@Schema(description = "状态：0.正常；1.停用；")
	private String status;

	@Schema(description = "排序")
	private Integer sort;

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

	@Schema(description = "租户ID")
	private String tenantId;

}
