package com.aryn.cloud.user.api.dto;

import com.aryn.cloud.user.api.entity.EnterpriseBenefit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 企业权益保存入参（管理端新建/修改）。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "企业权益保存DTO")
public class EnterpriseBenefitSaveDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "权益基础信息")
	private EnterpriseBenefit benefit;

	@Schema(description = "挂载的企业等级ID集合")
	private List<String> levelIds;

}
