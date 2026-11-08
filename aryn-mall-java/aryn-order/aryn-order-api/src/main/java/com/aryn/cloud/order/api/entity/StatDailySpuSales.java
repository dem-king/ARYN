package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SPU 社证统计 T+1 快照（商品卡「月N+人已下单 / 月回头客N+人 / X%人回购」底表）。
 *
 * <p>每日由 {@code spuSalesStatJobHandler} 按租户聚合写一行：窗口为截至 stat_date
 * 的近 N 天（默认 30），口径与热销榜/常购清单同源（order_info.status IN ('2','3','4')
 * 且订单、明细均未删除，窗口按 order_info.create_time）。同一 (租户, SPU, 快照日)
 * 唯一，重跑覆盖。读取侧只取每个 SPU 的 stat_date 最大行。
 *
 * <p>纯统计表：无逻辑删除、无业务写入方，历史数据暂不清理（万级 SPU × 365 行/年量级可控）。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Data
@Schema(description = "SPU社证统计T+1快照")
@TableName("stat_daily_spu_sales")
public class StatDailySpuSales implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "租户ID")
	private String tenantId;

	@Schema(description = "统计快照日（窗口截至当日）")
	private LocalDate statDate;

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "近N天有效订单去重买家数")
	private Integer buyerCnt;

	@Schema(description = "近N天购买≥2次的去重买家数（回购口径=不同订单数≥2）")
	private Integer repeatBuyerCnt;

	@Schema(description = "回购率% = repeat_buyer_cnt / buyer_cnt * 100")
	private BigDecimal repurchaseRate;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

}
