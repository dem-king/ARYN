
package com.aryn.cloud.promotion.api.dto;

import com.aryn.cloud.promotion.api.enums.CouponUserStatusEnum;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class CouponUserReqDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String id;

	private CouponUserStatusEnum couponUserStatusEnum;

}
