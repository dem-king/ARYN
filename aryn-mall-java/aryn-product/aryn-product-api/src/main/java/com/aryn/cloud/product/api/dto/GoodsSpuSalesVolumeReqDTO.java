
package com.aryn.cloud.product.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class GoodsSpuSalesVolumeReqDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String spuId;

	private Integer salesVolume;

}
