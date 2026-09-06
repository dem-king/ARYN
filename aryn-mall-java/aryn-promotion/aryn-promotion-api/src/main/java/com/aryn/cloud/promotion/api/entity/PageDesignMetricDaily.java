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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 页面装修每日聚合指标。
 * <p>
 * 按 页面/版本/日期/组件类型 聚合访问、点击与渲染错误计数；
 * 上报走 ON DUPLICATE KEY UPDATE 累加，不在装修读取链路执行重查询。
 *
 * @author 雨滴kian
 * @date 2026/09/06
 */
@Data
@Schema(description = "页面装修每日聚合指标")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("page_design_metric_daily")
public class PageDesignMetricDaily extends Model<PageDesignMetricDaily> {

	/**
	 * 页面级指标的组件类型占位值。
	 */
	public static final String COMPONENT_PAGE = "-";

	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private String id;

	@Schema(description = "页面ID")
	private String pageDesignId;

	@Schema(description = "发布版本ID")
	private String versionId;

	@Schema(description = "指标日期")
	private LocalDate metricDate;

	@Schema(description = "组件类型，页面级为 -")
	private String componentType;

	@Schema(description = "页面访问数")
	private Long viewCount;

	@Schema(description = "组件点击数")
	private Long clickCount;

	@Schema(description = "渲染错误数")
	private Long errorCount;

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
