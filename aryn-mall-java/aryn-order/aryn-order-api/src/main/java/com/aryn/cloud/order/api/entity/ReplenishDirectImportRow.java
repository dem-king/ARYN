package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 清单直下单导入解析行。
 *
 * <p>解析口径与 {@link SharedCartImportRow} 完全一致（同一套
 * ReplenishImportExcel / ReplenishImportClassifier / 商品域匹配），
 * 行内容**只留在服务端**：确认时客户端只回传「行号 → 处置动作」，
 * 按 {@code uk(tenant_id, import_id, row_no)} 锚定服务端解析行为准，防篡改。
 *
 * @author aryn
 * @since 2026/11/7
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("replenish_direct_import_row")
public class ReplenishDirectImportRow extends Model<ReplenishDirectImportRow> {

	public static final String RESULT_OK = "OK";

	public static final String RESULT_UNMATCHED = "UNMATCHED";

	public static final String RESULT_SPEC_CHANGED = "SPEC_CHANGED";

	public static final String RESULT_OVER_STOCK = "OVER_STOCK";

	public static final String RESULT_INVALID_QTY = "INVALID_QTY";

	public static final String RESULT_OFF_SHELF = "OFF_SHELF";

	/** 未填数量：客户没打算采购这一行（目录模板里的绝大多数行），不是错误 */
	public static final String RESULT_NOT_FILLED = "NOT_FILLED";

	public static final String ACTION_ACCEPT_SPEC = "ACCEPT_SPEC";

	public static final String ACTION_ADJUST_QTY = "ADJUST_QTY";

	public static final String ACTION_REPLACE_SKU = "REPLACE_SKU";

	public static final String ACTION_SKIP = "SKIP";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String importId;

	/** 行号（从1开始，不含表头） */
	private Integer rowNo;

	private String rawCode;

	private String rawName;

	private String rawSpec;

	private Integer rawQuantity;

	private String rawRemark;

	/** 匹配方式：CODE / NAME / AMBIGUOUS / NONE */
	private String matchType;

	private String matchedSkuId;

	private String matchedSpuId;

	private String matchedName;

	private String matchedSpec;

	private BigDecimal matchedPrice;

	private Integer matchedStock;

	/**
	 * 本次采购数量（Excel 数量列解析值）。
	 *
	 * <p>列名沿用 {@code planned_quantity}：与 {@link SharedCartImportRow} 同款映射，
	 * 该列名随「计划量」模型落地，增量脚本禁止 DROP 存量列，故显式映射原列名。
	 */
	@TableField("planned_quantity")
	private Integer quantity;

	/** 服务端建议数量（超库存调减 / 数量异常就近取整；无建议时为空） */
	private Integer suggestedQuantity;

	private String resultType;

	private String resultMessage;

	private String resolvedAction;

	private String resolvedSkuId;

	private Integer resolvedQuantity;

	private LocalDateTime resolvedTime;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
