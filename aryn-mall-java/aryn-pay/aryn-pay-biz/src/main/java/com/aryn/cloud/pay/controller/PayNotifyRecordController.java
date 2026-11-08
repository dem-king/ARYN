
package com.aryn.cloud.pay.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.entity.PayNotifyRecord;
import com.aryn.cloud.pay.service.IPayNotifyRecordService;
import com.aryn.cloud.pay.service.PayNotifyAck;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/notify")
@RequiredArgsConstructor
@Tag(description = "notify", name = "支付回调")
public class PayNotifyRecordController {

	private final IPayNotifyRecordService payNotifyRecordService;

	@Operation(summary = "微信支付回调通知（旧版，仅存量迁移窗口）")
	@PostMapping("/wx/{tenantId}/{terminalType}")
	public ResponseEntity<Void> notifyOrderWx(@PathVariable("tenantId") String tenantId,
			@PathVariable("terminalType") String terminalType, @RequestBody String notifyData) {
		return withTenant(tenantId, () -> payNotifyRecordService.wxPayNotify(tenantId, terminalType, notifyData));
	}

	@Operation(summary = "支付宝支付回调（旧版，仅存量迁移窗口）")
	@PostMapping("/alipay/{tenantId}/{terminalType}")
	public ResponseEntity<String> notifyOrderAli(@PathVariable("tenantId") String tenantId,
			@PathVariable("terminalType") String terminalType, HttpServletRequest request) {
		return withTenantAli(tenantId, () -> payNotifyRecordService.aliPayNotify(tenantId, terminalType, request));
	}

	@Operation(summary = "微信退款回调通知（旧版，仅存量迁移窗口）")
	@PostMapping("/refunds/wx/{tenantId}/{terminalType}")
	public ResponseEntity<Void> notifyRefundOrderWx(@PathVariable("tenantId") String tenantId,
			@PathVariable("terminalType") String terminalType, @RequestBody String notifyData) {
		return withTenant(tenantId,
				() -> payNotifyRecordService.wxPayRefundNotify(tenantId, terminalType, notifyData));
	}

	@Operation(summary = "微信支付回调通知（身份版本路由）")
	@PostMapping("/v2/wx/{tenantId}/{revisionId}")
	public ResponseEntity<Void> notifyOrderWxV2(@PathVariable("tenantId") String tenantId,
			@PathVariable("revisionId") String revisionId, @RequestBody String notifyData) {
		return withTenant(tenantId, () -> payNotifyRecordService.wxPayNotifyV2(tenantId, revisionId, notifyData));
	}

	@Operation(summary = "支付宝支付回调（身份版本路由）")
	@PostMapping("/v2/alipay/{tenantId}/{revisionId}")
	public ResponseEntity<String> notifyOrderAliV2(@PathVariable("tenantId") String tenantId,
			@PathVariable("revisionId") String revisionId, HttpServletRequest request) {
		return withTenantAli(tenantId, () -> payNotifyRecordService.aliPayNotifyV2(tenantId, revisionId, request));
	}

	@Operation(summary = "微信退款回调通知（身份版本路由）")
	@PostMapping("/v2/refunds/wx/{tenantId}/{revisionId}")
	public ResponseEntity<Void> notifyRefundOrderWxV2(@PathVariable("tenantId") String tenantId,
			@PathVariable("revisionId") String revisionId, @RequestBody String notifyData) {
		return withTenant(tenantId,
				() -> payNotifyRecordService.wxPayRefundNotifyV2(tenantId, revisionId, notifyData));
	}

	@Operation(summary = "通过id查询")
	@SaCheckPermission("pay:paynotifyrecord:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(payNotifyRecordService.getById(id));
	}

	@Operation(summary = "分页查询")
	@SaCheckPermission("pay:paynotifyrecord:page")
	@GetMapping("/page")
	public Result getPage(Page page, PayNotifyRecord payNotifyRecord) {
		return Result.success(payNotifyRecordService.page(page, Wrappers.query(payNotifyRecord)));
	}

	private ResponseEntity<Void> withTenant(String tenantId, java.util.function.Supplier<PayNotifyAck> action) {
		ArynTenantContextHolder.removeTenantId();
		if (!StringUtils.hasText(tenantId)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}
		ArynTenantContextHolder.setTenantId(tenantId);
		try {
			PayNotifyAck ack = action.get();
			if (ack.success()) {
				// 微信成功 ACK 使用 204 无包体
				return ResponseEntity.noContent().build();
			}
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

	private ResponseEntity<String> withTenantAli(String tenantId, java.util.function.Supplier<PayNotifyAck> action) {
		ArynTenantContextHolder.removeTenantId();
		if (!StringUtils.hasText(tenantId)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PayConstants.ALIPAY_FAIL);
		}
		ArynTenantContextHolder.setTenantId(tenantId);
		try {
			PayNotifyAck ack = action.get();
			if (ack.success()) {
				return ResponseEntity.ok(PayConstants.ALIPAY_SUCCESS);
			}
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PayConstants.ALIPAY_FAIL);
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

}
