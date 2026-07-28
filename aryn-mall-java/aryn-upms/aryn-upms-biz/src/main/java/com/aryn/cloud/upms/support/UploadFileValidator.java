package com.aryn.cloud.upms.support;

import cn.hutool.core.io.FileUtil;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
public class UploadFileValidator {

	private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

	private static final Set<String> DELIVERY_EVIDENCE_EXTENSIONS = Set.of("jpg", "jpeg", "png");

	public void validate(MultipartFile file) {
		validate(file, ALLOWED_EXTENSIONS, false);
	}

	public void validateDeliveryEvidence(MultipartFile file) {
		validate(file, DELIVERY_EVIDENCE_EXTENSIONS, true);
	}

	private void validate(MultipartFile file, Set<String> allowedExtensions, boolean deliveryEvidence) {
		if (file == null || file.isEmpty()) {
			throw new ArynBusinessException("上传文件不能为空");
		}
		if (file.getSize() > MAX_FILE_SIZE) {
			throw new ArynBusinessException("上传文件不能超过10MB");
		}

		String extension = FileUtil.extName(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
		String contentType = file.getContentType();
		if (!allowedExtensions.contains(extension)
				|| (StringUtils.hasText(contentType) && !contentType.startsWith("image/")
						&& !"application/octet-stream".equalsIgnoreCase(contentType))) {
			throw unsupportedImage(deliveryEvidence);
		}

		try (InputStream inputStream = file.getInputStream()) {
			byte[] header = inputStream.readNBytes(12);
			if (!matchesExtension(header, extension)) {
				throw unsupportedImage(deliveryEvidence);
			}
		}
		catch (IOException exception) {
			throw new ArynBusinessException("读取上传文件失败");
		}
	}

	private boolean matchesExtension(byte[] header, String extension) {
		return switch (extension) {
			case "jpg", "jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
			case "png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
			case "gif" -> startsWithText(header, "GIF87a") || startsWithText(header, "GIF89a");
			case "bmp" -> startsWithText(header, "BM");
			case "webp" -> header.length >= 12 && startsWithText(header, "RIFF")
					&& "WEBP".equals(new String(header, 8, 4, StandardCharsets.US_ASCII));
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

	private boolean startsWithText(byte[] header, String expected) {
		byte[] prefix = expected.getBytes(StandardCharsets.US_ASCII);
		if (header.length < prefix.length) {
			return false;
		}
		for (int index = 0; index < prefix.length; index++) {
			if (header[index] != prefix[index]) {
				return false;
			}
		}
		return true;
	}

	private ArynBusinessException unsupportedImage(boolean deliveryEvidence) {
		String message = deliveryEvidence ? "仅支持真实的 JPG、PNG 图片" : "仅支持 JPG、PNG、GIF、BMP、WEBP 图片";
		return new ArynBusinessException(message);
	}

}
