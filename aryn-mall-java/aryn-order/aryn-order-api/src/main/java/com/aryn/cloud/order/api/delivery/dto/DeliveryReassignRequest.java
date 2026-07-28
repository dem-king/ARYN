package com.aryn.cloud.order.api.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/** 改派或再次配送请求。 */
@Data
public class DeliveryReassignRequest implements Serializable {

	private static final long serialVersionUID = 1L;

	@NotBlank
	private String assigneeId;
	@NotNull
	private Integer version;
	@NotBlank
	private String requestId;
	@NotBlank
	private String reasonCode;
	private String description;

}
