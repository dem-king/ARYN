package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.user.api.entity.MemberOrderGrowth;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberOrderGrowthMapper extends BaseMapper<MemberOrderGrowth> {

	@Insert("""
			INSERT INTO member_order_growth
			(id, order_id, order_no, user_id, goods_payment_amount, points_awarded, tenant_id, create_time)
			VALUES
			(#{id}, #{orderId}, #{orderNo}, #{userId}, #{goodsPaymentAmount}, #{pointsAwarded}, #{tenantId}, #{createTime})
			ON DUPLICATE KEY UPDATE id = id
			""")
	int insertIfAbsent(MemberOrderGrowth growth);

}
