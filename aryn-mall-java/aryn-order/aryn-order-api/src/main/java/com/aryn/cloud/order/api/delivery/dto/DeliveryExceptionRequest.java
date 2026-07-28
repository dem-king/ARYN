package com.aryn.cloud.order.api.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 配送员上报异常请求。 */
@Data
public class DeliveryExceptionRequest implements Serializable {

	private static final long serialVersionUID = 1L;

	@NotNull
	private Integer version;
	@NotBlank
	private String requestId;
	@NotBlank
	private String reasonCode;
	@NotBlank
	private String description;
	private List<String> materialIds;

}
