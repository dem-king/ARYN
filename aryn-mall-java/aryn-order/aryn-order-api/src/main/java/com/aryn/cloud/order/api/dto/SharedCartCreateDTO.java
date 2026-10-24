package com.aryn.cloud.order.api.dto;

import com.aryn.cloud.order.api.entity.SharedCart;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创建共享购物车 DTO。
 *
 * <p>两种车按 {@code cartType} 分流（缺省船供，与存量行为一致）：
 * 船供车必须携带 vesselId + vesselCallId 且发起人为该船在船成员；
 * 岸上企业车必须携带 userAddressId（发起人地址簿），不校验船舶域。
 * 字段级校验无法按类型条件化，统一在服务层 create 里判断。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@Schema(description = "创建共享购物车DTO")
public class SharedCartCreateDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "车类型：1船供整车（默认）；2岸上企业")
	private String cartType;

	@Schema(description = "船舶ID（船供车必填）")
	private String vesselId;

	@Schema(description = "靠港计划ID（船供车必填）")
	private String vesselCallId;

	@Schema(description = "收货地址ID（岸上车必填，须为发起人地址簿中的地址）")
	private String userAddressId;

	@Schema(description = "收集截止时间")
	private LocalDateTime expiresAt;

	@Schema(description = "备注")
	private String remark;

	/**
	 * 归一车类型：空或未知值按船供处理（存量调用方不传 cartType 即船供）。
	 */
	public String normalizedCartType() {
		return SharedCart.CART_TYPE_SHORE.equals(cartType) ? SharedCart.CART_TYPE_SHORE : SharedCart.CART_TYPE_SHIP;
	}

}
