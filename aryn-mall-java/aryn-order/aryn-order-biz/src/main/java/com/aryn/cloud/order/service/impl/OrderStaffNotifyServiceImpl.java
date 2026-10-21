package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.api.constant.MessageJumpConstants;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderRefund;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.service.OrderStaffNotifyService;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 订单事件工作人员站内通知实现。
 *
 * <p>收件人解析复用货到付款收款预警的口径（按 ROLE_ADMIN 角色码查租户下有效员工），
 * 逐人下发 {@link MessageSendCommand}；幂等由站内信 (source_type, source_key) 唯一键
 * 兜底，eventId 含业务主键，重复触发不会重复入箱。
 *
 * <p><b>本类立即发送，不感知事务，调用时机由调用方负责</b>：事务内调用必须用
 * {@code TransactionalMqUtils.sendAfterCommit} 包一层，否则回滚后会留下误报；
 * 反过来，在 AFTER_COMMIT 监听器里调用时不要再包（此时事务同步仍处于激活态，
 * 新注册的回调不会被执行，通知会被静默丢弃）。收件人查询与发送失败只告警。
 *
 * <p>跳转负载统一指向管理端可直达的详情深链：订单创建/取消为
 * {@code /order/detail?id={orderId}}（订单详情静态路由按 query.id 查询），
 * 退款申请为 {@code /order/order-refund?refundId={refundId}}（退单列表页按 query 自动打开该退单）。
 *
 * @author Aetheryn
 * @date 2026/10/16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStaffNotifyServiceImpl implements OrderStaffNotifyService {

	private static final String CATEGORY = "ORDER";

	private static final String BIZ_ORDER_CREATE = "ORDER_CREATE";

	private static final String BIZ_ORDER_CANCEL = "ORDER_CANCEL";

	private static final String BIZ_ORDER_REFUND_APPLY = "ORDER_REFUND_APPLY";

	/** 订单详情静态路由：管理端按 route.query.id 调 getById 取详情。 */
	private static final String ORDER_DETAIL_ROUTE = "/order/detail";

	/** 退单管理列表路由：前端按 query.refundId 自动打开对应退单弹窗。 */
	private static final String REFUND_LIST_ROUTE = "/order/order-refund";

	/** Dubbo 远程接口走引用注入：boot 模式 injvm、cloud 模式直连。 */
	@DubboReference
	private RemoteMessageStaffService remoteMessageStaffService;

	private final OrderInfoMapper orderInfoMapper;

	private final RocketMQTemplate rocketMQTemplate;

	private final ObjectMapper objectMapper;

	@Override
	public void notifyOrderCreated(OrderInfo order) {
		if (order == null || order.getId() == null) {
			return;
		}
		String amount = amountText(order.getPaymentPrice(), order.getTotalPrice());
		boolean cashOnDelivery = MallOrderConstants.PAYMENT_TYPE_3.equals(order.getPaymentType());
		String title = "新订单待处理";
		String summary = "订单 " + order.getOrderNo() + " 金额 " + amount + " 元"
				+ (cashOnDelivery ? "（货到付款）" : "（待支付）");
		String content = "收到新订单 " + order.getOrderNo() + "，金额 " + amount + " 元，收货人 "
				+ displayOr(order.getRecipientName(), "未填写") + "。"
				+ (cashOnDelivery ? "货到付款订单，无需等待支付，请及时安排配送。" : "请关注买家支付情况并及时处理。");
		String tenantId = resolveTenant(order.getTenantId());
		String eventId = "order-created:admin:" + order.getId();
		String cardTitle = "订单 " + displayOr(order.getOrderNo(), order.getId());
		sendToAdmins(tenantId, eventId, title, summary, content, BIZ_ORDER_CREATE, order.getId(),
				ORDER_DETAIL_ROUTE + "?id=" + order.getId(), amount, cardTitle);
	}

	@Override
	public void notifyOrderCanceled(OrderInfo order, CancelSource source) {
		if (order == null || order.getId() == null) {
			return;
		}
		CancelSource cancelSource = source == null ? CancelSource.OTHER : source;
		String amount = amountText(order.getPaymentPrice(), order.getTotalPrice());
		String title = cancelSource.getTitle();
		String summary = "订单 " + order.getOrderNo() + "（金额 " + amount + " 元）已取消";
		String content = "订单 " + order.getOrderNo() + " 已取消，金额 " + amount + " 元，收货人 "
				+ displayOr(order.getRecipientName(), "未填写") + "。取消来源：" + cancelSource.getTitle() + "。"
				+ (cancelSource == CancelSource.TIMEOUT ? "如为该买家长期未支付，可关注是否需要跟进。" : "");
		String tenantId = resolveTenant(order.getTenantId());
		String eventId = "order-canceled:admin:" + order.getId();
		String cardTitle = "订单 " + displayOr(order.getOrderNo(), order.getId());
		sendToAdmins(tenantId, eventId, title, summary, content, BIZ_ORDER_CANCEL, order.getId(),
				ORDER_DETAIL_ROUTE + "?id=" + order.getId(), amount, cardTitle);
	}

	@Override
	public void notifyRefundApplied(OrderRefund refund) {
		if (refund == null || refund.getId() == null) {
			return;
		}
		String amount = refund.getRefundAmount() == null ? "" : refund.getRefundAmount().toPlainString();
		String refundNo = displayOr(refund.getRefundTradeNo(), refund.getId());
		String orderNo = lookupOrderNo(refund.getOrderId());
		String title = "新的退款申请待审核";
		String summary = "退款单 " + refundNo + " 申请退款 " + amount + " 元，待审核";
		String content = "买家提交了退款申请（退款单号 " + refundNo + "，订单 " + displayOr(orderNo, refund.getOrderId())
				+ "，金额 " + amount + " 元）。原因：" + displayOr(refund.getRefundReason(), "未填写")
				+ "。请尽快到退单管理审核。";
		String tenantId = resolveTenant(refund.getTenantId());
		String eventId = "order-refund-apply:admin:" + refund.getId();
		String cardTitle = "退款单 " + refundNo;
		sendToAdmins(tenantId, eventId, title, summary, content, BIZ_ORDER_REFUND_APPLY, refund.getId(),
				REFUND_LIST_ROUTE + "?refundId=" + refund.getId(), amount, cardTitle);
	}

	/**
	 * 租户取值：实体上的 tenant_id 由租户拦截器只注入 SQL、不回填实体，
	 * 创建链路拿不到，因此退回当前租户上下文（与订单取消/计价现有写法同口径）。
	 */
	private String resolveTenant(String entityTenantId) {
		return entityTenantId != null && !entityTenantId.isBlank() ? entityTenantId
				: ArynTenantContextHolder.getTenantId();
	}

	/** 退款单只存订单 ID，通知里展示订单号需要回查；查不到就退回订单 ID 展示。 */
	private String lookupOrderNo(String orderId) {
		if (orderId == null || orderId.isBlank()) {
			return null;
		}
		try {
			OrderInfo order = orderInfoMapper.selectById(orderId);
			return order == null ? null : order.getOrderNo();
		}
		catch (Exception e) {
			log.warn("退款申请通知回查订单号失败 orderId={}: {}", orderId, e.getMessage());
			return null;
		}
	}

	/**
	 * 收件人解析 + 逐人下发；收件人查询与发送失败只告警，不影响业务事实。
	 */
	private void sendToAdmins(String tenantId, String eventId, String title, String summary, String content,
			String bizType, String bizId, String route, String amount, String cardTitle) {
		if (tenantId == null || tenantId.isBlank()) {
			log.warn("订单事件通知跳过：租户缺失 eventId={}", eventId);
			return;
		}
		List<StaffMessageRecipientVO> admins;
		try {
			admins = remoteMessageStaffService.queryRecipientsByRoleCode(tenantId, CommonConstants.ROLE_ADMIN_CODE);
		}
		catch (Exception e) {
			log.warn("订单事件通知收件人查询失败，跳过 eventId={}: {}", eventId, e.getMessage());
			return;
		}
		if (CollectionUtils.isEmpty(admins)) {
			return;
		}
		for (StaffMessageRecipientVO admin : admins) {
			MessageSendCommand command = new MessageSendCommand();
			command.setEventId(eventId);
			command.setTenantId(tenantId);
			command.setRecipientType(MessageIdentityType.SYS_USER.name());
			command.setRecipientId(admin.getId());
			command.setRecipientName(admin.getNickname());
			command.setCategory(CATEGORY);
			command.setTitle(title);
			command.setSummary(summary);
			command.setContent(content);
			command.setBizType(bizType);
			command.setBizId(bizId);
			// 卡片负载带业务标识，前端只读 amount 展示金额；退款单 ID 不再占用 orderId 键
			command.setCardPayload(json(
					Map.of("bizType", bizType, "bizId", bizId, "title", cardTitle, "amount", amount)));
			// jump_payload 落 json 列：站内路由必须包成 JSON 对象，裸串会被消费端校验拒绝
			command.setJumpPayload(json(Map.of(MessageJumpConstants.ROUTE, route)));
			command.setChannels(List.of("IN_APP"));
			try {
				rocketMQTemplate.convertAndSend(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC, command);
			}
			catch (Exception e) {
				log.warn("订单事件通知发送失败 eventId={} recipient={}: {}", eventId, admin.getId(), e.getMessage());
			}
		}
	}

	private String amountText(BigDecimal paymentPrice, BigDecimal totalPrice) {
		BigDecimal amount = paymentPrice != null ? paymentPrice : totalPrice;
		return amount == null ? "0" : amount.toPlainString();
	}

	private String displayOr(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private String json(Map<String, String> payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("订单事件通知 payload 序列化失败", e);
		}
	}

}
