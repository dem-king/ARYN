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
 * 页面装修主题。
 * <p>
 * 草稿通过 v3 文档的 themeRef 引用主题；发布时由服务端将主题内容固化为
 * themeSnapshot 写入发布快照，主题后续修改不影响已发布历史版本。
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
@Data
@Schema(description = "页面装修主题")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("page_design_theme")
public class PageDesignTheme extends Model<PageDesignTheme> {

	/**
	 * 系统主题：否。
	 */
	public static final String SYSTEM_NO = "0";

	/**
	 * 系统主题：是。
	 */
	public static final String SYSTEM_YES = "1";

	/**
	 * 状态：正常。
	 */
	public static final String STATUS_ENABLED = "0";

	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键（同时作为 v3 文档 themeRef 令牌）")
	private String id;

	@Schema(description = "主题名称")
	private String themeName;

	@Schema(description = "品牌主色")
	private String primaryColor;

	@Schema(description = "页面背景色")
	private String pageBackgroundColor;

	@Schema(description = "导航栏背景色")
	private String navigationColor;

	@Schema(description = "导航栏文字色")
	private String navigationTextColor;

	@Schema(description = "全局圆角（px）")
	private Integer radius;

	@Schema(description = "系统主题：0.否；1.是；")
	private String systemFlag;

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
