package com.aryn.cloud.order.api.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/** 首次派单请求。 */
@Data
public class DeliveryAssignRequest implements Serializable {

	private static final long serialVersionUID = 1L;

	@NotBlank
	private String assigneeId;
	@NotNull
	private Integer version;
	@NotBlank
	private String requestId;
	private String remark;

}
