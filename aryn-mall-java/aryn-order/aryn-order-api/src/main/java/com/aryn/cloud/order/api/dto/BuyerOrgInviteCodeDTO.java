package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 生成采购主体邀请码 DTO。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Data
@Schema(description = "采购主体邀请码生成DTO")
public class BuyerOrgInviteCodeDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "最大使用次数，0 表示不限")
	private Integer maxUses;

	@Schema(description = "有效期（小时），默认 168，最大 720")
	private Integer expireHours;

	@Schema(description = "备注")
	private String remark;

}
