package com.aryn.cloud.product.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品编码映射（IMPA/ISSA/条码/内部编码/供应商编码）。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_code_mapping")
public class ProductCodeMapping extends Model<ProductCodeMapping> {

	public static final String TYPE_IMPA = "IMPA";

	public static final String TYPE_ISSA = "ISSA";

	public static final String TYPE_BARCODE = "BARCODE";

	public static final String TYPE_INTERNAL = "INTERNAL";

	public static final String TYPE_SUPPLIER = "SUPPLIER";

	public static final String SOURCE_MANUAL = "MANUAL";

	public static final String SOURCE_IMPORT = "IMPORT";

	public static final String SOURCE_AI = "AI";

	public static final String SOURCE_EXTERNAL = "EXTERNAL";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 商品SPU ID */
	private String spuId;

	/** 商品SKU ID */
	private String skuId;

	/** 编码类型：IMPA/ISSA/BARCODE/INTERNAL/SUPPLIER */
	private String codeType;

	/** 编码值 */
	private String codeValue;

	/** 匹配来源：MANUAL人工/IMPORT导入/AI智能/EXTERNAL外部 */
	private String matchSource;

	/** 匹配置信度（0-100，人工为空） */
	private BigDecimal confidence;

	/** 状态：1生效 0停用 */
	private String status;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
