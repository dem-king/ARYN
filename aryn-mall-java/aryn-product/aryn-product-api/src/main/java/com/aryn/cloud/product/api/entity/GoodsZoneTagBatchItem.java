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
 * 专区标签批量操作批次明细：逐商品记录变更前后快照与执行结果，供操作记录与撤销使用。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "专区标签批量操作批次明细")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("goods_zone_tag_batch_item")
public class GoodsZoneTagBatchItem extends Model<GoodsZoneTagBatchItem> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "批次ID")
	private String batchId;

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "商品名称快照")
	private String spuName;

	@Schema(description = "变更类型：ADD/UPDATE/REMOVE/NO_CHANGE/INVALID/FAILED")
	private String changeType;

	@Schema(description = "是否成功")
	private Boolean success;

	@Schema(description = "错误码")
	private String errorCode;

	@Schema(description = "结果说明")
	private String message;

	@Schema(description = "变更前快照 JSON（SPU 专区字段 + SKU 覆盖）")
	private String beforeSnapshot;

	@Schema(description = "变更后快照 JSON（SPU 专区字段 + SKU 覆盖）")
	private String afterSnapshot;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "逻辑删除：0显示 1隐藏")
	private String delFlag;

}
