package com.aryn.cloud.product.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 商品导入错误行。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_import_error")
public class ProductImportError extends Model<ProductImportError> {

	public static final String TYPE_EMPTY_NAME = "EMPTY_NAME";

	public static final String TYPE_DUPLICATE_CODE = "DUPLICATE_CODE";

	public static final String TYPE_DUPLICATE_SKU = "DUPLICATE_SKU";

	public static final String TYPE_ILLEGAL_PRICE = "ILLEGAL_PRICE";

	public static final String TYPE_ILLEGAL_STOCK = "ILLEGAL_STOCK";

	public static final String TYPE_ILLEGAL_QTY_RULE = "ILLEGAL_QTY_RULE";

	public static final String TYPE_UNKNOWN_CATEGORY = "UNKNOWN_CATEGORY";

	public static final String TYPE_UNKNOWN_BRAND = "UNKNOWN_BRAND";

	public static final String TYPE_MISSING_UPDATE_TARGET = "MISSING_UPDATE_TARGET";

	public static final String TYPE_CROSS_TENANT_CODE = "CROSS_TENANT_CODE";

	public static final String TYPE_OTHER = "OTHER";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 导入任务ID */
	private String jobId;

	/** 行号（从1开始） */
	private Integer rowNo;

	/** 错误类型 */
	private String errorType;

	/** 错误说明 */
	private String errorMessage;

	/** 原始行内容（受控JSON） */
	private String rawContent;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
