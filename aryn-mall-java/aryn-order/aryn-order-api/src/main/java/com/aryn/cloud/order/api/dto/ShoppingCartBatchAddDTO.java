package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 购物车批量加购请求。
 *
 * <p>场景：首页「清单还差 / 今日补给单」勾选多个商品后一次提交，
 * 也用于后续"按单加购"。服务端逐条复用单条加购的库存与数量规则校验，
 * 避免前端串行调用 N 次（N 次往返 + 多规格商品逐个弹层）。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@Schema(description = "购物车批量加购DTO")
public class ShoppingCartBatchAddDTO {

	/** 单次批量上限：防止一次请求写入过多行拖垮事务与库存校验 */
	public static final int MAX_BATCH_SIZE = 50;

	@NotEmpty(message = "加购明细不能为空")
	@Size(max = MAX_BATCH_SIZE, message = "单次最多加购50项")
	@Valid
	@Schema(description = "加购明细列表")
	private List<ShoppingCartCreateDTO> items;

}
