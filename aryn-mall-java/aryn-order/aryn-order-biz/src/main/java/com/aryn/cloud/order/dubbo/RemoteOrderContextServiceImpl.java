package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.api.remote.RemoteOrderContextService;
import com.aryn.cloud.order.api.vo.OrderContextVO;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 会员最近订单上下文远程实现（客服工作台信息面板用）。
 *
 * <p>口径：租户 + 会员过滤、未删除订单、按下单时间倒序、limit 归一化 1..10（默认 3）；
 * 只读、无权限项。为复用现有 BaseMapper、不改并行会话正在修改的 OrderInfoMapper.xml，
 * 这里用 LambdaQueryWrapper 拼条件 + {@code LIMIT}；商品摘要本批次降级为 null（见 VO 注释）。</p>
 *
 * <p>租户上下文处理沿用 order 域既有 Dubbo 实现约定：查询前把线程租户快照替换为显式入参并还原，
 * 保证 MyBatis 租户拦截器注入的 tenant_id 与契约口径一致。</p>
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteOrderContextServiceImpl implements RemoteOrderContextService {

	private static final int DEFAULT_LIMIT = 3;
	private static final int MAX_LIMIT = 10;

	private final OrderInfoMapper orderInfoMapper;

	@Override
	public List<OrderContextVO> queryRecentOrders(String tenantId, String customerId, int limit) {
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(customerId)) {
			return List.of();
		}
		int normalizedLimit = normalizeLimit(limit);
		String snapshot = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			List<OrderInfo> orders = orderInfoMapper.selectList(Wrappers.<OrderInfo>lambdaQuery()
				.eq(OrderInfo::getTenantId, tenantId)
				.eq(OrderInfo::getUserId, customerId)
				.eq(OrderInfo::getDelFlag, CommonConstants.NO)
				.orderByDesc(OrderInfo::getCreateTime)
				.last("LIMIT " + normalizedLimit));
			if (orders == null || orders.isEmpty()) {
				return List.of();
			}
			return orders.stream().map(this::toVO).toList();
		}
		catch (RuntimeException exception) {
			// 订单域异常按空数据降级：上下文面板不能阻塞客服会话（契约见 P2-A-会员订单上下文契约.md §3.4）。
			log.warn("查询会员最近订单失败，按空数据降级 tenantId={}, customerId={}, limit={}", tenantId, customerId,
					normalizedLimit, exception);
			return List.of();
		}
		finally {
			restoreTenant(snapshot);
		}
	}

	/** limit 归一化：≤0 → 默认 3；>10 → 截断为 10。 */
	static int normalizeLimit(int limit) {
		if (limit <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(limit, MAX_LIMIT);
	}

	private OrderContextVO toVO(OrderInfo order) {
		OrderContextVO vo = new OrderContextVO();
		vo.setOrderId(order.getId());
		vo.setOrderNo(order.getOrderNo());
		vo.setStatus(order.getStatus());
		vo.setStatusName(OrderStatusEnum.getValue(order.getStatus()));
		vo.setPayStatus(order.getPayStatus());
		vo.setPayAmount(resolvePayAmount(order));
		vo.setCreateTime(order.getCreateTime());
		// 商品摘要本批次降级为 null（避免为上下文面板扩展订单明细查询/改既有 mapper XML）。
		vo.setProductSummary(null);
		vo.setDeliveryWay(order.getDeliveryWay());
		return vo;
	}

	/** 支付金额：优先实收金额，缺失回退应付支付金额（两者均空时返回 null，前端按 0/— 展示）。 */
	private BigDecimal resolvePayAmount(OrderInfo order) {
		return order.getActualPayPrice() != null ? order.getActualPayPrice() : order.getPaymentPrice();
	}

	private void restoreTenant(String snapshot) {
		if (snapshot == null) {
			ArynTenantContextHolder.removeTenantId();
		}
		else {
			ArynTenantContextHolder.setTenantId(snapshot);
		}
	}

}
