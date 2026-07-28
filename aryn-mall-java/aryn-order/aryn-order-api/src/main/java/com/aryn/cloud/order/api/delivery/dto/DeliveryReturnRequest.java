package com.aryn.cloud.order.api.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/** 配送退回处理请求。 */
@Data
public class DeliveryReturnRequest implements Serializable {

	private static final long serialVersionUID = 1L;

	@NotNull
	private Integer version;
	@NotBlank
	private String requestId;
	private String reasonCode;
	private String description;

}
