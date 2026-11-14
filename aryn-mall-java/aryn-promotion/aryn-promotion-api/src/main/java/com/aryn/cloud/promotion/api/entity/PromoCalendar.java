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
 * 采购档期。
 *
 * <p>B2B 经营节奏的锚点：以周度档期/靠港档期/自然月/自定义时间窗组织选品与价格，
 * 替代零售整点秒杀。活动通过 promotion_activity.calendar_id 归属档期，
 * 档期未生效（草稿/停用/窗口外）时其下活动不参与试算。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "采购档期")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "promo_calendar")
public class PromoCalendar extends Model<PromoCalendar> {

	/** 锚点类型：周度档期 */
	public static final String ANCHOR_WEEKLY = "1";

	/** 锚点类型：靠港档期（anchor_ref = vessel_call_id） */
	public static final String ANCHOR_VESSEL_CALL = "2";

	/** 锚点类型：自然月 */
	public static final String ANCHOR_MONTHLY = "3";

	/** 锚点类型：自定义时间窗 */
	public static final String ANCHOR_CUSTOM = "4";

	/** 状态：草稿 */
	public static final String STATUS_DRAFT = "0";

	/** 状态：生效 */
	public static final String STATUS_ACTIVE = "1";

	/** 状态：停用 */
	public static final String STATUS_DISABLED = "2";

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "档期名称")
	private String calendarName;

	@Schema(description = "锚点类型：1周度档期 2靠港档期 3自然月 4自定义时间窗")
	private String anchorType;

	@Schema(description = "锚点引用：靠港计划ID（anchor_type=2）等")
	private String anchorRef;

	@Schema(description = "档期开始")
	private LocalDateTime startTime;

	@Schema(description = "档期结束")
	private LocalDateTime endTime;

	@Schema(description = "状态：0草稿 1生效 2停用")
	private String status;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "租户ID")
	private String tenantId;

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

}
