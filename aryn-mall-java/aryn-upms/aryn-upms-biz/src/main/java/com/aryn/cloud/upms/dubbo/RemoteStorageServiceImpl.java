package com.aryn.cloud.upms.dubbo;

import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.storage.handler.StorageFactory;
import com.aryn.cloud.upms.api.remote.RemoteStorageService;
import com.aryn.cloud.upms.service.ISysStorageConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;

/**
 * 存储能力远程实现（客服消息图片上传等内部 Dubbo 调用方使用）。
 *
 * <p>复用租户级存储配置（sys_storage_config）与 common-storage 的 {@link StorageFactory}，
 * 行为与 {@code SysUploadController#appUpload} 的公开上传一致：只返回 URL，不写 sys_material，
 * 也不依赖管理端素材上传权限。租户上下文由 Dubbo 过滤器 {@code ArynDubboRequestFilter}
 * 透传（boot 模式同 JVM 直接复用调用方线程上下文）。</p>
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteStorageServiceImpl implements RemoteStorageService {

	private final ISysStorageConfigService sysStorageConfigService;

	private final StorageFactory storageFactory;

	@Override
	public String uploadPublicFile(byte[] content, String originalFilename, String contentType, long size,
			String bizTag) {
		if (content == null || content.length == 0) {
			throw new ArynBusinessException("上传文件不能为空");
		}
		String tenantId = ArynTenantContextHolder.getTenantId();
		SysStorageConfigDTO config = sysStorageConfigService.getConfig();
		if (config == null) {
			throw new ArynBusinessException("当前租户未配置启用的文件存储，请联系管理员在系统设置-文件存储配置中启用");
		}
		String fileName = StringUtils.hasText(originalFilename) ? originalFilename : "upload.bin";
		log.info("远程文件上传 tenantId={}, bizTag={}, fileName={}, contentType={}, size={}", tenantId, bizTag,
				fileName, contentType, size);
		try {
			return storageFactory.getStrategy(config.getType())
				.uploadFile(config, new ByteArrayInputStream(content), fileName, contentType, size);
		}
		catch (RuntimeException exception) {
			log.error("远程文件上传失败 tenantId={}, bizTag={}, fileName={}", tenantId, bizTag, fileName, exception);
			throw new ArynBusinessException("文件上传失败，请稍后重试");
		}
	}

}
