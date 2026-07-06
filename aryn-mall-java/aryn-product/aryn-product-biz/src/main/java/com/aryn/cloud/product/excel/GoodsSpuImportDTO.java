
package com.aryn.cloud.product.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品导入DTO
 *
 * @author aryn
 * @since 2026/7/5
 */
@Data
@ColumnWidth(20)
public class GoodsSpuImportDTO {

	@ExcelProperty("商品名称")
	@ColumnWidth(30)
	private String name;

	@ExcelProperty("子标题")
	@ColumnWidth(30)
	private String subTitle;

	@ExcelProperty("一级类目")
	private String categoryFirstName;

	@ExcelProperty("二级类目")
	private String categorySecondName;

	@ExcelProperty("品牌名称")
	private String brandName;

	@ExcelProperty("销售价")
	private BigDecimal salesPrice;

	@ExcelProperty("原价")
	private BigDecimal originalPrice;

	@ExcelProperty("成本价")
	private BigDecimal costPrice;

	@ExcelProperty("库存")
	private Integer stock;

	@ExcelProperty("运费类型")
	private String freightType;

	@ExcelProperty("固定运费")
	private BigDecimal fixedFreightPrice;

	@ExcelProperty("描述")
	@ColumnWidth(40)
	private String description;

}