package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "秒杀场次DTO")
public class SeckillSessionDTO {

	@Schema(description = "场次名称")
	@NotBlank(message = "场次名称不能为空")
	private String sessionName;

	@Schema(description = "场次开始时间")
	@NotNull(message = "场次开始时间不能为空")
	private LocalDateTime startTime;

	@Schema(description = "场次结束时间")
	@NotNull(message = "场次结束时间不能为空")
	private LocalDateTime endTime;

	@Schema(description = "商品列表")
	@Valid
	private List<SeckillGoodsDTO> goodsList;
}