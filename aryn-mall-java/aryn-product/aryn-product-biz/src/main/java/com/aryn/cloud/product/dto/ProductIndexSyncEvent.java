package com.aryn.cloud.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 商品索引同步事件
 * <p>
 * SPU 增删改时发送到 RocketMQ 的消息体。
 * </p>
 *
 * @author aryn
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductIndexSyncEvent implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * 操作类型：add / update / delete
	 */
	private String action;

	/**
	 * SPU 主键
	 */
	private String spuId;

}