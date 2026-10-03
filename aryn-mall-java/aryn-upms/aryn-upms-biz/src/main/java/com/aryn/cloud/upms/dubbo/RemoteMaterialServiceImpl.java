package com.aryn.cloud.upms.dubbo;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.upms.api.entity.SysMaterial;
import com.aryn.cloud.upms.api.remote.RemoteMaterialService;
import com.aryn.cloud.upms.service.ISysMaterialService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 素材远程查询服务实现
 *
 * @author aryn
 * @since 2026/10/2
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteMaterialServiceImpl implements RemoteMaterialService {

	private final ISysMaterialService sysMaterialService;

	@Override
	public Map<String, String> mapUrlByIds(List<String> materialIds) {
		if (CollUtil.isEmpty(materialIds)) {
			return Collections.emptyMap();
		}
		return sysMaterialService.listByIds(materialIds).stream()
			.filter(material -> StrUtil.isNotBlank(material.getUrl()))
			.collect(Collectors.toMap(SysMaterial::getId, SysMaterial::getUrl, (first, second) -> first));
	}

}
