package com.aryn.cloud.order.api.delivery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 租户商城配送范围。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("order_delivery_area")
public class OrderDeliveryArea extends Model<OrderDeliveryArea> {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;
	private String scopeLevel;
	private String areaCode;
	private String provinceCode;
	private String provinceName;
	private String cityCode;
	private String cityName;
	private String districtCode;
	private String districtName;
	private String enabled;
	private String tenantId;
	@TableField(fill = FieldFill.INSERT)
	private String createBy;
	@TableField(fill = FieldFill.UPDATE)
	private String updateBy;
	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;
	@TableField(fill = FieldFill.UPDATE)
	private LocalDateTime updateTime;
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	private String delFlag;

}
