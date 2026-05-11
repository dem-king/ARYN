package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 签到记录
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "签到记录")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "sign_in_record")
public class SignInRecord extends Model<SignInRecord> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "签到日期")
	private LocalDate signDate;

	@Schema(description = "连续签到天数")
	private Integer consecutiveDay;

	@Schema(description = "获得积分")
	private Integer rewardPoint;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "租户ID")
	private String tenantId;

}
