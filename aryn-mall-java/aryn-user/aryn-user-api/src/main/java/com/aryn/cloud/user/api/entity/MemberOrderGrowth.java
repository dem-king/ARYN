package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("member_order_growth")
public class MemberOrderGrowth {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String orderId;

	private String orderNo;

	private String userId;

	private BigDecimal goodsPaymentAmount;

	private Integer pointsAwarded;

	private String tenantId;

	private LocalDateTime createTime;

}
