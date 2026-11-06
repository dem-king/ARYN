package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;

/**
 * 消息专用图片上传结果。
 *
 * <p>{@code fileId} 与 {@code url} 相同：沿用 C 端把上传 URL 直接当 fileId 的既有约定
 * （发送 IMAGE 消息时 payload.fileId 只做非空/长度校验）；前端直接把本对象字段组装进
 * IMAGE payload 即可（{@code fileId/url/mimeType/name/size}）。</p>
 */
@Data
public class MessageUploadVO implements Serializable {

	/** 文件标识（= url，见类注释）。 */
	private String fileId;

	/** 可访问 URL：路径必含 /{tenantId}/，满足发送侧租户隔离校验。 */
	private String url;

	/** 清洗后的原始文件名。 */
	private String name;

	/** 文件字节数。 */
	private Long size;

	/** 规范化后的 MIME：image/jpeg、image/png、image/webp（与发送侧 IMAGE 白名单一致）。 */
	private String contentType;

}
