
package com.aryn.cloud.product.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.aryn.cloud.common.myabtis.handler.JsonArrayStringTypeHandler;
import com.aryn.cloud.product.api.hanlder.GoodsAttrTagListTypeHandler;
import com.aryn.cloud.product.api.vo.GoodsSocialBubbleVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品spu
 *
 * @author 雨滴kian
 * @since 2022/2/22 14:28
 */
@Data
@Schema(description = "商品spu")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "goods_spu", autoResultMap = true)
public class GoodsSpu extends Model<GoodsSpu> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "商品名称")
	private String name;

	@Schema(description = "子标题")
	private String subTitle;

	/**
	 * 商详首屏卖点：白名单内的 HTML 片段（p/br/span/strong/em/u/s，
	 * span 仅保留 color/font-weight/font-size/text-align）。
	 *
	 * <p>写入前经 {@code GoodsIntroductionSanitizer} 清洗，C 端用 mp-html 渲染，
	 * 因此运营可以逐商品配色；为空时首屏整块不渲染。
	 */
	@Schema(description = "商品卖点（白名单HTML片段，商详首屏）")
	private String introduction;

	@Schema(description = "商品属性标签（商详首屏属性卡）")
	@TableField(value = "attr_tags", typeHandler = GoodsAttrTagListTypeHandler.class, jdbcType = JdbcType.VARCHAR)
	private List<GoodsAttrTag> attrTags;

	@Schema(description = "商品图地址")
	@TableField(typeHandler = JsonArrayStringTypeHandler.class)
	private String[] spuUrls;

	@Schema(description = "状态：0.下架；1.上架")
	private String status;

	@Schema(description = "销量")
	private Integer salesVolume;

	@Schema(description = "一级类目主键")
	private String categoryFirstId;

	@Schema(description = "二级类目主键")
	private String categorySecondId;

	@Schema(description = "品牌主键")
	private String brandId;

	@Schema(description = "描述")
	private String description;

	@Schema(description = "多规格：0.否；1.是")
	private String enableSpecs;

	@Schema(description = "租户id")
	private String tenantId;

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

	@Schema(description = "商品类目名称")
	@TableField(exist = false)
	private String categoryName;

	@Schema(description = "品牌名称")
	@TableField(exist = false)
	private String brandName;

	@Schema(description = "商品sku")
	@TableField(exist = false)
	private List<GoodsSku> goodsSkus;

	@TableField(exist = false)
	private String collectId;

	/**
	 * 商品主图像素宽/高：C 端列表按图片真实比例锁卡片占位高度，图片加载前后不跳动。
	 *
	 * <p>不落库，由列表接口在服务层按主图 URL 解析后回填；解析失败时为空，
	 * 前端按 1:1 兜底（旧口径，同样不会跳动）。</p>
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@Schema(description = "商品主图宽（像素，C 端列表辅助字段，不落库）")
	@TableField(exist = false)
	private Integer picWidth;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@Schema(description = "商品主图高（像素，C 端列表辅助字段，不落库）")
	@TableField(exist = false)
	private Integer picHeight;

	/**
	 * 商详口碑气泡（轮播图浮层）：社证文案/好评率/买家头像，仅 C 端详情接口在
	 * 服务层装配（{@code getApiSpuById}），任何一步失败整体置 null，气泡不渲染。
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@Schema(description = "商详口碑气泡；null=不展示")
	@TableField(exist = false)
	private GoodsSocialBubbleVO socialBubble;

	@Schema(description = "总库存 sku库存累计")
	private Integer stock;

	@Schema(description = "运费类型：0.包邮；1.固定运费")
	private String freightType;

	@Schema(description = "固定运费金额，仅当运费类型为1时生效")
	private BigDecimal fixedFreightPrice;

	@Schema(description = "商品销售价 sku最低销售价")
	private BigDecimal salesPrice;

	@Schema(description = "商品原价  sku最低原价")
	private BigDecimal originalPrice;

	/**
	 * 商品成本价（内部经营数据，不下发 C 端）。
	 *
	 * <p>NON_NULL：C 端出参经 {@code GoodsCostPriceMasker} 置空后，该字段从 JSON
	 * 中整个消失，而不是留一个 {@code "costPrice": null}。管理端出参有值时不受影响。
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@Schema(description = "商品成本价 sku最低成本价")
	private BigDecimal costPrice;

	@Schema(description = "会员专区标签：0否 1是")
	private Integer isMemberItem;

	/**
	 * 商品级会员折扣率（如 0.9000）；空表示用会员等级折扣率。
	 *
	 * <p>属于内部定价规则，C 端出参经 {@link com.aryn.cloud.product.api.util.GoodsCostPriceMasker}
	 * 置空后不下发，避免暴露底价。
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@Schema(description = "商品级会员折扣率，如0.9000；空=用等级折扣率")
	private BigDecimal memberDiscountRate;

	/** 会员固定价（迁移旧活动用）；与按率算价取低。C 端不下发。 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@Schema(description = "会员固定价；与按率算价取低")
	private BigDecimal memberPriceFixed;

	/** 会员价下限（防击穿），空=0.01。C 端不下发。 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@Schema(description = "会员价下限（防击穿），空=0.01")
	private BigDecimal memberPriceFloor;

	@Schema(description = "捡漏专区标签：0否 1是")
	private Integer isClearanceItem;

	@Schema(description = "出清固定价，与 clearanceDiscountRate 二选一")
	private BigDecimal clearancePrice;

	@Schema(description = "出清折扣率（对 originalPrice 计算）")
	private BigDecimal clearanceDiscountRate;

	@Schema(description = "出清截止时间，到期自动退出专区与出清价")
	private LocalDateTime clearanceExpireTime;

	/**
	 * 本次保存是否改动了专区相关字段（非库字段）。
	 *
	 * <p>控制器据此决定是否触发系统活动同步，避免每次普通商品编辑都全量重算租户专区。
	 */
	@JsonIgnore
	@TableField(exist = false)
	private boolean zoneChanged;

}
