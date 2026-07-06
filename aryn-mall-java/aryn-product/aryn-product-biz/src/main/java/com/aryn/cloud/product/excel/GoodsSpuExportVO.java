
package com.aryn.cloud.product.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品导出VO
 *
 * @author aryn
 * @since 2026/7/5
 */
@Data
@ColumnWidth(20)
public class GoodsSpuExportVO {

	@ExcelProperty("商品名称")
	@ColumnWidth(30)
	private String name;

	@ExcelProperty("子标题")
	@ColumnWidth(30)
	private String subTitle;

	@ExcelProperty("类目名称")
	private String categoryName;

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

	@ExcelProperty("销量")
	private Integer salesVolume;

	@ExcelProperty("状态")
	private String status;

	@ExcelProperty("运费类型")
	private String freightType;

	@ExcelProperty("固定运费")
	private BigDecimal fixedFreightPrice;

	@ExcelProperty("创建时间")
	@ColumnWidth(25)
	private String createTime;

}