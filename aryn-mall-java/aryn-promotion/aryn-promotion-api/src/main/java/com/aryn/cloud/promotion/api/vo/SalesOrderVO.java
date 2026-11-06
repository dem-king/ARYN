package com.aryn.cloud.promotion.api.vo;

import com.aryn.cloud.promotion.api.entity.SalesOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 销售业绩单视图
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售业绩单视图")
@EqualsAndHashCode(callSuper = true)
public class SalesOrderVO extends SalesOrder {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "买家昵称")
	private String buyerNickname;

}
