package com.aryn.cloud.upms.api.remote;

/**
 * 存储能力远程契约（内部 Dubbo）。
 *
 * <p>供其它业务域（如客服消息图片上传）复用租户级文件存储配置与 common-storage 上传能力，
 * 避免跨域直连 upms 库/引入 upms-biz 依赖，也不需要授予 {@code upms:material:add} 权限。</p>
 *
 * <p>当前只暴露公开读文件的上传：文件按租户目录隔离，返回可访问 URL，不写 sys_material 素材表。</p>
 */
public interface RemoteStorageService {

	/**
	 * 上传公开读文件并返回可访问 URL。
	 * @param content 文件内容（调用方已完成大小/类型/魔数校验）
	 * @param originalFilename 原始文件名（用于取扩展名；实现侧应清洗路径）
	 * @param contentType 文件 MIME
	 * @param size 文件字节数（与 content.length 一致）
	 * @param bizTag 业务归属标记（仅用于服务端日志/审计，不落库）
	 * @return 可访问 URL（本地存储形如 /file/local/{tenantId}/xxx，对象存储为公网 URL）
	 */
	String uploadPublicFile(byte[] content, String originalFilename, String contentType, long size, String bizTag);

}
