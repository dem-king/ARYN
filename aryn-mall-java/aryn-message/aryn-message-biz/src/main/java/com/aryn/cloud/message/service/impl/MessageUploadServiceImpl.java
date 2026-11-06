package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.vo.conversation.MessageUploadVO;
import com.aryn.cloud.message.service.MessageUploadService;
import com.aryn.cloud.upms.api.remote.RemoteStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 客服消息专用图片上传实现。
 *
 * <p>安全口径：扩展名 + 声明 Content-Type + 文件头魔数三重校验，SVG/BMP 等不放行；
 * 大小上限 5MB（先于读取/落盘拦截，避免大文件进 Dubbo）。落盘复用 upms 的
 * {@link RemoteStorageService}（common-storage 的 StorageFactory + 租户存储配置），
 * 返回 URL 必须包含 /{tenantId}/，与发送侧 IMAGE payload 的租户隔离校验保持一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageUploadServiceImpl implements MessageUploadService {

	/** 会话图片大小上限：5MB。 */
	private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
	private static final int MAX_FILE_NAME_LENGTH = 100;
	/** 与发送侧 ChatMessageServiceImpl.IMAGE_MIME_TYPES 保持一致（GIF 本期不支持）。 */
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	private static final Map<String, String> EXTENSION_CONTENT_TYPES = Map.of("jpg", "image/jpeg", "jpeg", "image/jpeg",
			"png", "image/png", "webp", "image/webp");
	private static final String OCTET_STREAM = "application/octet-stream";
	private static final String UPLOAD_BIZ_TAG = "service-chat";

	@DubboReference
	private final RemoteStorageService remoteStorageService;

	@Override
	public MessageUploadVO uploadImage(String tenantId, MultipartFile file) {
		String extension = validateAndGetExtension(file);
		byte[] content = readContent(file);
		if (!matchesImageMagic(content, extension)) {
			throw new ArynBusinessException("图片格式不合法");
		}
		String fileName = sanitizeFileName(file.getOriginalFilename(), extension);
		String contentType = canonicalContentType(extension, file.getContentType());
		String url = uploadToStorage(tenantId, content, fileName, contentType);
		MessageUploadVO result = new MessageUploadVO();
		result.setFileId(url);
		result.setUrl(url);
		result.setName(fileName);
		result.setSize((long) content.length);
		result.setContentType(contentType);
		return result;
	}

	/** 基础校验并返回小写扩展名；大小在读取文件内容前拦截。 */
	private String validateAndGetExtension(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ArynBusinessException("上传文件不能为空");
		}
		if (file.getSize() > MAX_IMAGE_SIZE) {
			throw new ArynBusinessException("图片大小不能超过 5MB");
		}
		String extension = extensionOf(file.getOriginalFilename());
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw unsupportedImage();
		}
		String declared = file.getContentType();
		if (StringUtils.hasText(declared) && !OCTET_STREAM.equalsIgnoreCase(declared)
				&& !ALLOWED_CONTENT_TYPES.contains(declared.toLowerCase(Locale.ROOT))) {
			throw unsupportedImage();
		}
		return extension;
	}

	private byte[] readContent(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException exception) {
			log.warn("读取上传图片失败 fileName={}", file.getOriginalFilename(), exception);
			throw new ArynBusinessException("读取上传文件失败");
		}
	}

	private String uploadToStorage(String tenantId, byte[] content, String fileName, String contentType) {
		try {
			String url = remoteStorageService.uploadPublicFile(content, fileName, contentType, content.length,
					UPLOAD_BIZ_TAG);
			if (!StringUtils.hasText(url)) {
				throw new ArynBusinessException("图片上传失败，请稍后重试");
			}
			validateTenantScopedUrl(tenantId, url);
			return url.trim();
		}
		catch (ArynBusinessException exception) {
			// upms 业务错误（如未配置存储）原样透传中文提示。
			throw exception;
		}
		catch (RuntimeException exception) {
			ArynBusinessException business = extractBusinessException(exception);
			if (business != null) {
				throw business;
			}
			log.error("消息图片远程存储失败 tenantId={}, fileName={}, size={}", tenantId, fileName, content.length,
					exception);
			throw new ArynBusinessException("图片上传服务暂不可用，请稍后重试");
		}
	}

	/** 与发送侧 validateTenantFileUrl 同一租户路径约定：URL 路径必须包含 /{tenantId}/，否则发送会被拒。 */
	private void validateTenantScopedUrl(String tenantId, String url) {
		if (!StringUtils.hasText(tenantId)) {
			throw new ArynBusinessException("图片上传失败，请稍后重试");
		}
		String path = url.trim().split("[?#]", 2)[0];
		if (!path.contains("/" + tenantId + "/")) {
			log.error("存储返回的图片 URL 缺少租户路径 tenantId={}, url={}", tenantId, url);
			throw new ArynBusinessException("图片上传失败：文件地址未按租户隔离");
		}
	}

	/** Dubbo 可能把 provider 业务异常包装成 RpcException，沿 cause 链找回 ArynBusinessException 以保留中文提示。 */
	private ArynBusinessException extractBusinessException(Throwable error) {
		Throwable current = error;
		int depth = 0;
		while (current != null && depth++ < 5) {
			if (current instanceof ArynBusinessException business) {
				return business;
			}
			current = current.getCause();
		}
		return null;
	}

	private String extensionOf(String originalFilename) {
		if (!StringUtils.hasText(originalFilename)) {
			return "";
		}
		int dot = originalFilename.lastIndexOf('.');
		return dot < 0 || dot == originalFilename.length() - 1 ? ""
				: originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	/** 文件名清洗：去掉客户端可能携带的路径/控制字符，超长保留尾部（保留扩展名）。 */
	private String sanitizeFileName(String originalFilename, String extension) {
		String name = StringUtils.hasText(originalFilename) ? originalFilename : "image." + extension;
		int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}
		name = name.replace("\u0000", "").trim();
		if (!StringUtils.hasText(name)) {
			name = "image." + extension;
		}
		return name.length() <= MAX_FILE_NAME_LENGTH ? name : name.substring(name.length() - MAX_FILE_NAME_LENGTH);
	}

	private String canonicalContentType(String extension, String declared) {
		if (StringUtils.hasText(declared) && !OCTET_STREAM.equalsIgnoreCase(declared)) {
			return declared.toLowerCase(Locale.ROOT);
		}
		return EXTENSION_CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
	}

	/** 文件头魔数校验：扩展名与真实内容不符（改名伪造）直接拒绝。 */
	private boolean matchesImageMagic(byte[] content, String extension) {
		if (content == null || content.length < 3) {
			return false;
		}
		return switch (extension) {
			case "jpg", "jpeg" -> startsWith(content, 0xFF, 0xD8, 0xFF);
			case "png" -> startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
			case "webp" -> content.length >= 12 && startsWith(content, 0x52, 0x49, 0x46, 0x46)
					&& "WEBP".equals(new String(content, 8, 4, StandardCharsets.US_ASCII));
			default -> false;
		};
	}

	private boolean startsWith(byte[] actual, int... expected) {
		if (actual.length < expected.length) {
			return false;
		}
		for (int index = 0; index < expected.length; index++) {
			if (Byte.toUnsignedInt(actual[index]) != expected[index]) {
				return false;
			}
		}
		return true;
	}

	private ArynBusinessException unsupportedImage() {
		return new ArynBusinessException("仅支持 JPG/JPEG/PNG/WEBP 图片");
	}

}
