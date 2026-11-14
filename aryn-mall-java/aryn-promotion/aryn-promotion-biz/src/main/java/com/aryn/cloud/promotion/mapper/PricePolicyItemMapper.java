package com.aryn.cloud.promotion.mapper;

import com.aryn.cloud.promotion.api.entity.PricePolicyItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * PricePolicyItem 持久层（协议价 SKU 明细）。
 *
 * <p>唯一键 (tenant_id, policy_id, sku_id) 不含 del_flag：行被逻辑删除后重新配置同 SKU
 * 会撞唯一键，保存统一走 {@link #upsert}（ON DUPLICATE KEY UPDATE 复活行并覆盖价格），
 * 全仓唯一 INSERT 点。
 */
@Mapper
public interface PricePolicyItemMapper extends BaseMapper<PricePolicyItem> {

	/** 按唯一键 upsert：新行插入，历史行（含已逻辑删除）复活并覆盖价格与起订量。 */
	@Insert("""
			INSERT INTO `price_policy_item`
				(`id`, `policy_id`, `spu_id`, `sku_id`, `policy_price`, `policy_rate`, `min_qty`,
				 `tenant_id`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
			VALUES (#{item.id}, #{item.policyId}, #{item.spuId}, #{item.skuId}, #{item.policyPrice},
					#{item.policyRate}, #{item.minQty}, #{item.tenantId}, #{item.createBy}, NOW(),
					#{item.updateBy}, NOW(), '0')
			ON DUPLICATE KEY UPDATE
				`spu_id` = VALUES(`spu_id`),
				`policy_price` = VALUES(`policy_price`),
				`policy_rate` = VALUES(`policy_rate`),
				`min_qty` = VALUES(`min_qty`),
				`del_flag` = '0',
				`update_by` = VALUES(`update_by`),
				`update_time` = NOW()
			""")
	int upsert(@Param("item") PricePolicyItem item);

}

