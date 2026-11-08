package com.aryn.cloud.pay.config;

import com.alipay.api.AlipayClient;
import com.github.binarywang.wxpay.service.WxPayService;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import lombok.RequiredArgsConstructor;
import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付渠道客户端缓存。键为租户 + 身份版本 + 材料指纹，材料轮换即自然失效。
 */
@Component
@RequiredArgsConstructor
public class PayChannelClientFactory {

	private static final int MAX_CACHE_ENTRIES = 32;

	private final WxPayConfiguration wxPayConfiguration;

	private final AliPayConfiguration aliPayConfiguration;

	private final Map<String, WxPayService> wxCache = Collections
		.synchronizedMap(new LinkedHashMap<>(16, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, WxPayService> eldest) {
				if (size() > MAX_CACHE_ENTRIES) {
					closeQuietly(eldest.getValue());
					return true;
				}
				return false;
			}
		});

	private final Map<String, AlipayClient> aliCache = Collections
		.synchronizedMap(new LinkedHashMap<>(16, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, AlipayClient> eldest) {
				return size() > MAX_CACHE_ENTRIES;
			}
		});

	public WxPayService wxPayService(PayResolvedIdentity identity) {
		return wxCache.computeIfAbsent(identity.cacheKey(), key -> wxPayConfiguration.create(identity));
	}

	public AlipayClient alipayClient(PayResolvedIdentity identity) {
		return aliCache.computeIfAbsent(identity.cacheKey(), key -> aliPayConfiguration.create(identity));
	}

	public void clear() {
		synchronized (wxCache) {
			wxCache.values().forEach(PayChannelClientFactory::closeQuietly);
			wxCache.clear();
		}
		aliCache.clear();
	}

	public int cachedWxCount() {
		return wxCache.size();
	}

	public int cachedAliCount() {
		return aliCache.size();
	}

	private static void closeQuietly(WxPayService service) {
		try {
			CloseableHttpClient client = service.getConfig().getApiV3HttpClient();
			if (client != null) {
				client.close();
			}
		}
		catch (Exception ignored) {
			// 缓存淘汰时的关闭失败不影响支付主流程
		}
	}

}
