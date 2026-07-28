package com.aryn.cloud.order.api.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 配送员提交送达请求。 */
@Data
public class DeliveryCompleteRequest implements Serializable {

	private static final long serialVersionUID = 1L;

	@NotNull
	private Integer version;
	@NotBlank
	private String requestId;
	private List<String> materialIds;

}
