package com.aryn.cloud.product.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 专区标签批量操作批次：记录一次批量打标/移出/撤销的可追溯信息与同步结果。
 *
 * <p>批次明细见 {@link GoodsZoneTagBatchItem}；批次用于管理端操作记录与撤销。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "专区标签批量操作批次")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("goods_zone_tag_batch")
public class GoodsZoneTagBatch extends Model<GoodsZoneTagBatch> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "批次号（展示用）")
	private String batchNo;

	@Schema(description = "操作人ID")
	private String operatorId;

	@Schema(description = "操作人姓名快照")
	private String operatorName;

	@Schema(description = "会员专区动作：ADD/REMOVE/NONE")
	private String memberAction;

	@Schema(description = "低价捡漏动作：ADD/REMOVE/NONE")
	private String clearanceAction;

	@Schema(description = "会员规则快照 JSON")
	private String memberRule;

	@Schema(description = "出清规则快照 JSON")
	private String clearanceRule;

	@Schema(description = "是否清除 SKU 精调")
	private Boolean overwriteSkuRules;

	@Schema(description = "请求商品数")
	private Integer total;

	@Schema(description = "实际变化数")
	private Integer changed;

	@Schema(description = "新增数")
	private Integer added;

	@Schema(description = "更新数")
	private Integer updated;

	@Schema(description = "移出数")
	private Integer removed;

	@Schema(description = "无变化数")
	private Integer noChange;

	@Schema(description = "失败数")
	private Integer failed;

	@Schema(description = "同步状态：SUCCESS/FAILED/SKIPPED")
	private String syncStatus;

	@Schema(description = "同步失败原因")
	private String syncMessage;

	@Schema(description = "撤销状态：NONE/UNDONE")
	private String undoStatus;

	@Schema(description = "撤销时间")
	private LocalDateTime undoTime;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "逻辑删除：0显示 1隐藏")
	private String delFlag;

}
