package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.entity.OrderConfig;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.service.ICodPayRemindService;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 货到付款收款预警实现。
 *
 * <p>扫描条件：payment_type=3 且 pay_status=0 且已完成（COD 允许未收款先确认收货，
 * 「已完成未收款」是催收重点，退款中订单不提醒）。以收货时间为锚点，按
 * order_config.cod_pay_remind_hours 配置的小时数分轮提醒买家与租户管理员；
 * 幂等去重依赖站内信 (source_type, source_key) 唯一键，eventId 含订单与轮次。</p>
 *
 * @author Aetheryn
 * @date 2026/10/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodPayRemindServiceImpl implements ICodPayRemindService {

	private static final String BIZ_TYPE = "COD_PAY_REMIND";

	private static final String CATEGORY = "ORDER";

	/** 单租户单轮扫描上限，防止积压时一次发爆；未处理订单由下次调度继续兜。 */
	private static final int SCAN_LIMIT = 500;

	/** 扫描窗口下沿在最大提醒轮次之外再放给的追补缓冲天数（调度停摆期间漏发的单仍能补提醒）。 */
	private static final int CATCHUP_BUFFER_DAYS = 7;

	private static final String DEFAULT_REMIND_HOURS = "72,168";

	private static final DateTimeFormatter RECEIVED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final IOrderInfoService orderInfoService;

	private final IOrderConfigService orderConfigService;

	/** Dubbo 远程接口走引用注入：boot 模式 injvm、cloud 模式直连，避免构造器按类型注入与本地实现 bean 二义 */
	@DubboReference
	private RemoteMessageStaffService remoteMessageStaffService;

	private final RocketMQTemplate rocketMQTemplate;

	private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

	@Override
	public int remindTenant(String tenantId) {
		OrderConfig config = orderConfigService.getConfig();
		if (config == null) {
			return 0;
		}
		List<Integer> remindHours = parseRemindHours(config.getCodPayRemindHours());
		if (remindHours.isEmpty()) {
			return 0;
		}
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime windowStart = now.minusHours(remindHours.get(remindHours.size() - 1))
			.minusDays(CATCHUP_BUFFER_DAYS);
		List<OrderInfo> orders = orderInfoService.list(Wrappers.<OrderInfo>lambdaQuery()
			.select(OrderInfo::getId, OrderInfo::getUserId, OrderInfo::getOrderNo, OrderInfo::getPaymentPrice,
					OrderInfo::getTotalPrice, OrderInfo::getReceiverTime, OrderInfo::getRecipientName)
			.eq(OrderInfo::getPaymentType, MallOrderConstants.PAYMENT_TYPE_3)
			.eq(OrderInfo::getPayStatus, CommonConstants.NO)
			.eq(OrderInfo::getStatus, OrderStatusEnum.COMPLETED.getCode())
			.isNotNull(OrderInfo::getReceiverTime)
			.ge(OrderInfo::getReceiverTime, windowStart)
			.lt(OrderInfo::getReceiverTime, now.minusHours(remindHours.get(0)))
			.orderByAsc(OrderInfo::getReceiverTime)
			.last("limit " + SCAN_LIMIT));
		if (orders.isEmpty()) {
			return 0;
		}
		List<StaffMessageRecipientVO> admins = remoteMessageStaffService.queryRecipientsByRoleCode(tenantId,
				CommonConstants.ROLE_ADMIN_CODE);
		int sent = 0;
		for (OrderInfo order : orders) {
			long elapsedHours = Duration.between(order.getReceiverTime(), now).toHours();
			for (int round = 1; round <= remindHours.size(); round++) {
				if (remindHours.get(round - 1) > elapsedHours) {
					break;
				}
				sent += sendRemind(tenantId, order, round, elapsedHours, admins);
			}
		}
		return sent;
	}

	@Override
	public List<Integer> parseRemindHours(String configValue) {
		String value = configValue == null ? DEFAULT_REMIND_HOURS : configValue.trim();
		if (value.isEmpty()) {
			return List.of();
		}
		Set<Integer> hours = new LinkedHashSet<>();
		for (String part : value.split(",")) {
			try {
				int hour = Integer.parseInt(part.trim());
				if (hour > 0) {
					hours.add(hour);
				}
			}
			catch (NumberFormatException ignored) {
				log.warn("货到付款收款提醒配置含非法时间点，已忽略: {}", part);
			}
		}
		List<Integer> sorted = new ArrayList<>(hours);
		sorted.sort(Integer::compareTo);
		return sorted;
	}

	private int sendRemind(String tenantId, OrderInfo order, int round, long elapsedHours,
			List<StaffMessageRecipientVO> admins) {
		String eventIdSuffix = ":" + order.getId() + ":R" + round;
		BigDecimal amount = order.getPaymentPrice() != null ? order.getPaymentPrice() : order.getTotalPrice();
		String amountText = amount == null ? "" : amount.toPlainString();
		String receivedAt = order.getReceiverTime() == null ? "" : order.getReceiverTime().format(RECEIVED_AT_FORMAT);
		int sent = 0;
		if (order.getUserId() != null && !order.getUserId().isBlank()) {
			MessageSendCommand buyerCommand = baseCommand("cod-pay-remind:buyer" + eventIdSuffix, tenantId, order);
			buyerCommand.setRecipientType(MessageIdentityType.MALL_USER.name());
			buyerCommand.setRecipientId(order.getUserId());
			buyerCommand.setRecipientName(order.getRecipientName());
			buyerCommand.setTitle("货到付款订单待付款");
			buyerCommand.setSummary("订单 " + order.getOrderNo() + " 货款 " + amountText + " 元尚未支付");
			buyerCommand.setContent("您的货到付款订单 " + order.getOrderNo() + " 已于 " + receivedAt
					+ " 确认收货，货款 " + amountText + " 元尚未支付（已收货 " + elapsedHours
					+ " 小时）。请尽快完成线下付款。");
			buyerCommand.setCardPayload(json(Map.of("orderId", order.getId(), "title", "订单 " + order.getOrderNo(),
					"amount", amountText)));
			buyerCommand.setJumpPayload(json(Map.of("bizType", "ORDER", "bizId", order.getId())));
			sent += publish(buyerCommand);
		}
		if (CollectionUtils.isEmpty(admins)) {
			return sent;
		}
		for (StaffMessageRecipientVO admin : admins) {
			MessageSendCommand adminCommand = baseCommand("cod-pay-remind:admin" + eventIdSuffix, tenantId, order);
			adminCommand.setRecipientType(MessageIdentityType.SYS_USER.name());
			adminCommand.setRecipientId(admin.getId());
			adminCommand.setRecipientName(admin.getNickname());
			adminCommand.setTitle("货到付款订单收款提醒");
			adminCommand.setSummary("订单 " + order.getOrderNo() + " 已收货 " + elapsedHours + " 小时仍未确认收款");
			adminCommand.setContent("货到付款订单 " + order.getOrderNo() + "（应收 " + amountText
					+ " 元）已于 " + receivedAt + " 确认收货，已收货 " + elapsedHours + " 小时仍未确认收款（第 "
					+ round + " 轮提醒），请尽快到订单管理确认收款。");
			adminCommand.setJumpPayload("/order/order-info");
			sent += publish(adminCommand);
		}
		return sent;
	}

	private MessageSendCommand baseCommand(String eventId, String tenantId, OrderInfo order) {
		MessageSendCommand command = new MessageSendCommand();
		command.setEventId(eventId);
		command.setTenantId(tenantId);
		command.setCategory(CATEGORY);
		command.setBizType(BIZ_TYPE);
		command.setBizId(order.getId());
		command.setChannels(List.of("IN_APP"));
		return command;
	}

	private int publish(MessageSendCommand command) {
		try {
			rocketMQTemplate.convertAndSend(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC, command);
			return 1;
		}
		catch (Exception e) {
			log.warn("货到付款收款提醒发送失败 eventId={}: {}", command.getEventId(), e.getMessage());
			return 0;
		}
	}

	private String json(Map<String, String> payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		}
		catch (Exception e) {
			log.warn("货到付款收款提醒 payload 序列化失败: {}", e.getMessage());
			return null;
		}
	}

}
