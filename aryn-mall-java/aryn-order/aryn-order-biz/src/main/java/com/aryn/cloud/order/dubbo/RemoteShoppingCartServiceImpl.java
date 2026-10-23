package com.aryn.cloud.order.dubbo;

import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.order.api.remote.RemoteShoppingCartService;
import com.aryn.cloud.order.mapper.ShoppingCartMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 购物车远程服务实现：供 vessel 模块在靠港申报/排产生效时迁移购物车行归属。
 *
 * <p>迁移口径见 {@link RemoteShoppingCartService#reattachRowsToVesselCall}。
 * 全部参数显式传入并落到 SQL 过滤条件，不依赖调用链路上下文——boot 模式
 * Dubbo injvm 的提供方过滤器会清掉调用方线程的租户上下文，云模式下更是跨进程。
 *
 * @author aryn
 * @since 2026/10/4
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteShoppingCartServiceImpl implements RemoteShoppingCartService {

	private final ShoppingCartMapper shoppingCartMapper;

	@Override
	public int reattachRowsToVesselCall(String tenantId, String vesselId, String vesselCallId,
			List<String> staleCallIds) {
		if (StrUtil.isBlank(tenantId) || StrUtil.isBlank(vesselId) || StrUtil.isBlank(vesselCallId)) {
			log.warn("靠港迁移购物车行被跳过：tenantId/vesselId/vesselCallId 存在空白");
			return 0;
		}
		int rows = shoppingCartMapper.reattachRowsToVesselCall(tenantId, vesselId, vesselCallId,
				staleCallIds == null ? List.of() : staleCallIds);
		if (rows > 0) {
			log.info("靠港[{}]生效：船舶[{}]的 {} 行购物车归属顺延到新靠港", vesselCallId, vesselId, rows);
		}
		return rows;
	}

	@Override
	public int countDistinctSkuUsersSince(String tenantId, List<String> skuIds, LocalDateTime since) {
		if (StrUtil.isBlank(tenantId) || since == null || skuIds == null || skuIds.isEmpty()) {
			return 0;
		}
		// 只读统计：失败时由调用方降级为不展示，不抛异常影响会场加载。
		// 聚合查询恒返回一行（无匹配时为 0），mapper 返回原始 int，不做 null 兜底。
		return shoppingCartMapper.countDistinctSkuUsersSince(tenantId, skuIds, since);
	}

}
