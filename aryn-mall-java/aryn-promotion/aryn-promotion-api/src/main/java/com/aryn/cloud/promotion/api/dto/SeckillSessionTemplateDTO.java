package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
@Schema(description = "秒杀每日排期模板DTO")
public class SeckillSessionTemplateDTO {

	@Schema(description = "主键(编辑时传)")
	private String id;

	@Schema(description = "场次名称")
	@NotBlank(message = "场次名称不能为空")
	private String sessionName;

	@Schema(description = "每日开始时间")
	@NotNull(message = "场次开始时间不能为空")
	private LocalTime startTime;

	@Schema(description = "每日结束时间")
	@NotNull(message = "场次结束时间不能为空")
	private LocalTime endTime;

	@Schema(description = "生效星期(1-7逗号分隔，1=周一；空=每天)")
	private String weekdays;

	@Schema(description = "商品列表（每场计划库存）")
	@Valid
	private List<SeckillGoodsDTO> goodsList;
}
