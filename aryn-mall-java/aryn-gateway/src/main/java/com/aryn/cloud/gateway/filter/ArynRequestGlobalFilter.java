
package com.aryn.cloud.gateway.filter;

import com.aryn.cloud.common.core.constant.SecurityConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关全局拦截器 作用所有微服务系统
 *
 * @author 雨滴kian
 * @since 2022/2/18 11:55
 */
@Component
public class ArynRequestGlobalFilter implements GlobalFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpRequest newRequest = exchange.getRequest()
			.mutate()
			// 为请求追加 请求来源参数
			.header(SecurityConstants.SOURCE, SecurityConstants.SOURCE_OUTER)
			.build();
		ServerWebExchange newExchange = exchange.mutate().request(newRequest).build();
		return chain.filter(newExchange);
	}

}
