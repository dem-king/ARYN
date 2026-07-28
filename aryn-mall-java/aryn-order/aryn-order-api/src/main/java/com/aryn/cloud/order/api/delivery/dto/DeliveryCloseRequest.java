package com.aryn.cloud.order.api.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/** 管理员关闭配送任务请求。 */
@Data
public class DeliveryCloseRequest implements Serializable {

	private static final long serialVersionUID = 1L;

	@NotNull
	private Integer version;
	@NotBlank
	private String requestId;
	@NotBlank
	private String reasonCode;
	private String description;

}
