package com.aryn.cloud.upms.dubbo;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.upms.api.entity.SysMaterial;
import com.aryn.cloud.upms.api.remote.RemoteMaterialService;
import com.aryn.cloud.upms.service.ISysMaterialService;
import com.aryn.cloud.upms.support.PrivateFileUrlSigner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteMaterialServiceImpl implements RemoteMaterialService {

	private final ISysMaterialService sysMaterialService;

	private final PrivateFileUrlSigner privateFileUrlSigner;

	@Override
	public Map<String, String> mapUrlByIds(List<String> materialIds) {
		if (CollUtil.isEmpty(materialIds)) {
			return Collections.emptyMap();
		}
		return sysMaterialService.listByIds(materialIds).stream()
			.filter(material -> StrUtil.isNotBlank(material.getUrl()))
			.collect(Collectors.toMap(SysMaterial::getId, SysMaterial::getUrl, (first, second) -> first));
	}

	@Override
	public List<String> signPrivateUrls(List<String> urls) {
		if (CollUtil.isEmpty(urls)) {
			return Collections.emptyList();
		}
		return urls.stream().map(url -> {
			try {
				return privateFileUrlSigner.sign(url);
			}
			catch (Exception e) {
				// 签名失败时返回原 URL：下载端会拒绝匿名访问，不会退化为公开
				log.warn("私有素材URL签名失败 url={}", url, e);
				return url;
			}
		}).toList();
	}

}
