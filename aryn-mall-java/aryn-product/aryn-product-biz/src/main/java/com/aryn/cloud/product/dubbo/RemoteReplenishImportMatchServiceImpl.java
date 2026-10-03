package com.aryn.cloud.product.dubbo;

import com.aryn.cloud.product.api.dto.ReplenishImportMatchDTO;
import com.aryn.cloud.product.api.remote.RemoteReplenishImportMatchService;
import com.aryn.cloud.product.api.vo.ReplenishCatalogVO;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import com.aryn.cloud.product.service.IReplenishImportMatchService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 补给单导入匹配远程实现。 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteReplenishImportMatchServiceImpl implements RemoteReplenishImportMatchService {

	private final IReplenishImportMatchService replenishImportMatchService;

	@Override
	public List<ReplenishImportMatchVO> matchRows(String tenantId, List<ReplenishImportMatchDTO> rows) {
		return replenishImportMatchService.matchRows(tenantId, rows);
	}

	@Override
	public List<ReplenishImportMatchVO> matchSkuIds(String tenantId, List<String> skuIds) {
		return replenishImportMatchService.matchSkuIds(tenantId, skuIds);
	}

	@Override
	public ReplenishCatalogVO exportCatalog(String tenantId, int limit) {
		return replenishImportMatchService.exportCatalog(tenantId, limit);
	}

}
