package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * C端等级权益VO
 *
 * <p>只暴露展示字段：权益实体带 createBy/tenantId/delFlag 等内部字段，
 * 不能直接回给 C 端。
 *
 * @author 雨滴kian
 */
@Data
public class AppMemberBenefitVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "权益名称")
	private String benefitName;

	@Schema(description = "权益类型：1.折扣；2.免运费；3.专属优惠券；4.积分倍率")
	private String benefitType;

	@Schema(description = "权益值")
	private String benefitValue;

	@Schema(description = "描述")
	private String description;

}
