package com.aryn.cloud.product.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.GoodsZoneSkuSnapshotDTO;
import com.aryn.cloud.product.api.remote.RemoteGoodsZoneTagService;
import com.aryn.cloud.product.service.IGoodsZoneTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 商品专区标签远程实现：显式入参租户锁定上下文，调用结束还原。
 *
 * <p>与 RemoteMemberLevelServiceImpl 同款：Dubbo injvm 返回后租户上下文可能被清空，
 * 若查询依赖上下文会被注入 tenant_id = NULL 而返回 0 行。
 *
 * <p><b>查询失败必须抛出</b>：促销域同步需要区分「租户没有打标商品」与「查询失败」，
 * 若吞异常返回空列表，会把空规则写入系统活动并暂停价格，造成全租户专区价消失。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteGoodsZoneTagServiceImpl implements RemoteGoodsZoneTagService {

	private final IGoodsZoneTagService goodsZoneTagService;

	@Override
	public List<GoodsZoneSkuSnapshotDTO> listZoneSkus(String tenantId) {
		if (!StringUtils.hasText(tenantId)) {
			return List.of();
		}
		String snapshot = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			return goodsZoneTagService.listZoneSkus();
		}
		catch (Exception ex) {
			log.error("专区商品快照查询失败，中止本次同步以保护既有专区活动 tenant={}", tenantId, ex);
			throw new ArynBusinessException("专区商品快照查询失败，已中止同步");
		}
		finally {
			restoreTenant(snapshot);
		}
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
