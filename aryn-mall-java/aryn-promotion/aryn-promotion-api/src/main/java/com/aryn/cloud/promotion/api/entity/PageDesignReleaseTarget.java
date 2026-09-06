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
 * 页面装修灰度发布目标。
 * <p>
 * 记录一次灰度发布申请命中的租户/终端；App 端读取时按当前租户匹配灰度版本。
 *
 * @author 雨滴kian
 * @date 2026/09/06
 */
@Data
@Schema(description = "页面装修灰度发布目标")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("page_design_release_target")
public class PageDesignReleaseTarget extends Model<PageDesignReleaseTarget> {

	/**
	 * 终端：全部。
	 */
	public static final String TERMINAL_ALL = "all";

	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private String id;

	@Schema(description = "发布申请ID")
	private String releaseId;

	@Schema(description = "页面ID")
	private String pageDesignId;

	@Schema(description = "灰度目标租户ID")
	private String targetTenantId;

	@Schema(description = "灰度目标终端：all/h5/weapp")
	private String terminal;

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

	@Schema(description = "租户ID（申请归属租户）")
	private String tenantId;

}
