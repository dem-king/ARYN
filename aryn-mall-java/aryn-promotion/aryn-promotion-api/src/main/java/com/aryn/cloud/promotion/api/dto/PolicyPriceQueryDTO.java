package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * C 端协议价批量查询入参。
 *
 * <p>用户身份只从登录态读取（userId），采购主体/船舶上下文由 C 端按当前会话上下文传入
 * （岸上共享车/船上下文 store），与订单计价上下文同源；服务端不校验成员资格
 * （资格校验在订单链），本接口只做「价格展示」，成交仍以下单试算为准。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "协议价批量查询DTO")
public class PolicyPriceQueryDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "SKU ID 集合（优先）")
	private List<String> skuIds;

	@Schema(description = "SPU ID 集合（列表卡片聚合价场景；与 skuIds 同时存在时 skuIds 优先）")
	private List<String> spuIds;

	@Schema(description = "采购主体ID（岸上企业上下文）")
	private String buyerOrgId;

	@Schema(description = "船舶ID（船供上下文）")
	private String vesselId;

}
