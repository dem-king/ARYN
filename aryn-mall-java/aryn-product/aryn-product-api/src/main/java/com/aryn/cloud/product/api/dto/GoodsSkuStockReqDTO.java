
package com.aryn.cloud.product.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class GoodsSkuStockReqDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String skuId;

	private Integer stockNum;

	private String spuId;

	/** 乐观锁版本号，扣减库存时用于并发控制 */
	private Integer version;

}
