
package com.aryn.cloud.product.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.aryn.cloud.common.myabtis.handler.JsonArrayStringTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 商品评价
 *
 * @author 雨滴kian
 * @since 2022/3/10 17:06
 */
@Data
@Schema(description = "商品评价")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "goods_appraise", autoResultMap = true)
public class GoodsAppraise extends Model<GoodsAppraise> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "spuId")
	private String spuId;

	@Schema(description = "订单主键")
	private String orderId;

	@Schema(description = "子订单主键")
	private String orderItemId;

	@Schema(description = "用户主键")
	private String userId;

	@Schema(description = "头像")
	private String avatarUrl;

	@Schema(description = "昵称")
	private String nickname;

	@Schema(description = "图片")
	@TableField(typeHandler = JsonArrayStringTypeHandler.class)
	private String[] picUrls;

	@Schema(description = "商品评分")
	private Integer goodsScore;

	@Schema(description = "物流评分")
	private Integer logisticsScore;

	@Schema(description = "服务评分")
	private Integer serviceScore;

	@Schema(description = "商家回复")
	private String businessReply;

	@Schema(description = "回复时间")
	private LocalDateTime replyTime;

	@Schema(description = "评论内容")
	private String content;

	@Schema(description = "评价类型：0.用户主动评价；1.系统默认评价（默认评价不打分、不进好评率统计）")
	private Integer appraiseType;

	@Schema(description = "是否匿名：0.否；1.是")
	private Integer isAnonymous;

	@Schema(description = "评价标签")
	@TableField(typeHandler = JsonArrayStringTypeHandler.class)
	private String[] tags;

	@Schema(description = "追评内容")
	private String advanceContent;

	@Schema(description = "追评图片")
	@TableField(typeHandler = JsonArrayStringTypeHandler.class)
	private String[] advancePicUrls;

	@Schema(description = "追评时间（已追评后写入，仅允许追评一次）")
	private LocalDateTime advanceTime;

	@Schema(description = "评价修改时间（已修改后写入，仅允许修改一次）")
	private LocalDateTime modifyTime;

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

	@Schema(description = "租户id")
	private String tenantId;

	@TableField(exist = false)
	private String spuName;

	@TableField(exist = false, typeHandler = JsonArrayStringTypeHandler.class)
	private String[] spuUrls;

}
