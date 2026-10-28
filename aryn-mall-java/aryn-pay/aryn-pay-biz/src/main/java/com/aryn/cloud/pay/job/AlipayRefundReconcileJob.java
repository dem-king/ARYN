
package com.aryn.cloud.pay.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.pay.api.entity.PayRefundOrder;
import com.aryn.cloud.pay.handler.impl.AlipayRefundHandler;
import com.aryn.cloud.pay.mapper.PayRefundOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 支付宝退款「待确认」自动收敛。
 *
 * <p>渠道返回 fund_change=N 时无法判定是失败还是重复请求，支付回调也可能丢失；
 * 定时按退款单号主动查询，确认成功后补发退款通知，让订单侧退款状态最终一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayRefundReconcileJob {

	private static final int BATCH_SIZE = 50;

	private final PayRefundOrderMapper payRefundOrderMapper;

	private final AlipayRefundHandler alipayRefundHandler;

	@Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
	public void reconcilePendingAlipayRefunds() {
		List<PayRefundOrder> pending = payRefundOrderMapper.selectPendingAlipayReconcile(BATCH_SIZE);
		if (pending.isEmpty()) {
			return;
		}
		log.info("支付宝退款待确认核对开始，共{}笔", pending.size());
		for (PayRefundOrder refundOrder : pending) {
			ArynTenantContextHolder.setTenantId(refundOrder.getTenantId());
			try {
				alipayRefundHandler.reconcile(refundOrder);
			}
			catch (Exception exception) {
				log.warn("支付宝退款待确认核对失败 refundTradeNo={}", refundOrder.getRefundTradeNo(), exception);
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		}
	}

}
