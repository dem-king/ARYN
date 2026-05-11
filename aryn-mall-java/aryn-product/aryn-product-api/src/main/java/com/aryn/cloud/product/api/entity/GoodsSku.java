
package com.aryn.cloud.product.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.aryn.cloud.product.api.hanlder.SpecsListTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.ibatis.type.JdbcType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品sku
 *
 * @author 雨滴kian
 * @since 2022/2/22 14:28
 */
@Data
@Schema(description = "商品sku")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "goods_sku", autoResultMap = true)
public class GoodsSku extends Model<GoodsSku> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "spuId")
	private String spuId;

	@Schema(description = "销售价格（元）")
	private BigDecimal salesPrice;

	@Schema(description = "原价（元）")
	private BigDecimal originalPrice;

	@Schema(description = "成本价（元）")
	private BigDecimal costPrice;

	@Schema(description = "库存")
	private Integer stock;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

	@Schema(description = "版本号")
	@Version
	private Integer version;

	@TableField(exist = false)
	private GoodsSpu goodsSpu;

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "状态")
	private String status;

	@Schema(description = "规格数组")
	@TableField(value = "specs_arr", typeHandler = SpecsListTypeHandler.class, jdbcType = JdbcType.VARCHAR)
	private List<Specs> specsArr;

	@Schema(description = "图片地址")
	private String picUrl;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Specs implements Serializable {

		private static final long serialVersionUID = 1L;

		private String specsId;

		private String specsName;

		private String specsValueId;

		private String specsValueName;

	}

}
