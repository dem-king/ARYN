package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.vo.conversation.MessageUploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 客服消息专用图片上传服务。
 *
 * <p>只做文件校验与落盘调用，不产生会话消息；发送仍走 {@link ChatMessageService#send}（IMAGE + payload）。
 * 不依赖管理端素材上传权限（upms:material:add），存储能力经 upms 的 RemoteStorageService 内部 Dubbo 复用。</p>
 */
public interface MessageUploadService {

	/**
	 * 上传会话图片。
	 * @param tenantId 租户（用于校验存储返回 URL 的租户路径）
	 * @param file multipart 文件（jpg/jpeg/png/webp、≤5MB）
	 * @return 上传结果（fileId=url/url/name/size/contentType）
	 */
	MessageUploadVO uploadImage(String tenantId, MultipartFile file);

}
