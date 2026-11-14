package com.aryn.cloud.user.api.entity;

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
import java.util.List;

/**
 * 企业会员权益。
 *
 * <p>权益类型与取值口径见 {@link com.aryn.cloud.user.api.constant.EnterpriseMemberConstants}；
 * 多档聚合口径：折扣率取最小（最优）、返利率/账期取最大，免邮/开票/专属客服任一命中即生效。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "企业会员权益")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "enterprise_benefit")
public class EnterpriseBenefit extends Model<EnterpriseBenefit> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "权益名称")
	private String benefitName;

	@Schema(description = "权益类型：1企业折扣率 2免邮 3协议价政策池 4开票资质 5专属客服 6返利率(预留) 7账期天数(预留)")
	private String benefitType;

	@Schema(description = "权益值（折扣率0.9500/免邮1/政策ID集合/返利率百分数/账期天数）")
	private String benefitValue;

	@Schema(description = "权益说明")
	private String description;

	@Schema(description = "状态：0启用 1禁用")
	private String status;

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

	@Schema(description = "挂载等级ID集合（查询参数，非表字段）")
	@TableField(exist = false)
	private List<String> levelIds;

}
