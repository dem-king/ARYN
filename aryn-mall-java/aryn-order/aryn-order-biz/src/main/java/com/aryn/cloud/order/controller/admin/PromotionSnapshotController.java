package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.PromotionSnapshot;
import com.aryn.cloud.order.mapper.PromotionSnapshotMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单营销快照查询（历史订单价格依据，随订单详情展示）。
 *
 * @author aryn
 * @since 2026/9/12
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/promotion-snapshot")
@Tag(description = "promotion-snapshot-admin", name = "订单营销快照")
public class PromotionSnapshotController {

	private final PromotionSnapshotMapper promotionSnapshotMapper;

	@Operation(summary = "按订单查询营销快照")
	@SaCheckPermission("order:orderinfo:get")
	@GetMapping("/list")
	public Result<List<PromotionSnapshot>> list(@RequestParam("orderId") String orderId) {
		return Result.success(promotionSnapshotMapper.selectList(Wrappers.lambdaQuery(PromotionSnapshot.class)
				.eq(PromotionSnapshot::getTenantId, ArynTenantContextHolder.getTenantId())
				.eq(PromotionSnapshot::getOrderId, orderId)
				.orderByAsc(PromotionSnapshot::getCreateTime)));
	}

}
