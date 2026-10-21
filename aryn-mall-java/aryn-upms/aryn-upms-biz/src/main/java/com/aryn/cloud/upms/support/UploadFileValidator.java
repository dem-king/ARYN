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

	/** 素材类型：2=视频（对齐 sys_material.type 口径）；其余按图片处理 */
	private static final String MATERIAL_TYPE_VIDEO = "2";

	private static final long MAX_IMAGE_FILE_SIZE = 10L * 1024 * 1024;

	private static final long MAX_VIDEO_FILE_SIZE = 100L * 1024 * 1024;

	private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

	private static final Set<String> ALLOWED_VIDEO_EXTENSIONS = Set.of("mp4", "mov", "m4v", "webm");

	/** 图片校验：C 端上传、配送凭证等入口共用，不放行视频 */
	public void validate(MultipartFile file) {
		validateImage(file);
	}

	/** 管理端素材上传：type=2 走视频校验，其余仍按图片处理 */
	public void validate(MultipartFile file, String type) {
		if (MATERIAL_TYPE_VIDEO.equals(type)) {
			validateVideo(file);
			return;
		}
		validateImage(file);
	}

	private void validateImage(MultipartFile file) {
		checkNotEmpty(file);
		if (file.getSize() > MAX_IMAGE_FILE_SIZE) {
			throw new ArynBusinessException("上传文件不能超过10MB");
		}

		String extension = extractExtension(file);
		String contentType = file.getContentType();
		if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)
				|| (StringUtils.hasText(contentType) && !contentType.startsWith("image/")
						&& !"application/octet-stream".equalsIgnoreCase(contentType))) {
			throw unsupportedImage();
		}

		if (!matchesImageExtension(readHeader(file), extension)) {
			throw unsupportedImage();
		}
	}

	private void validateVideo(MultipartFile file) {
		checkNotEmpty(file);
		if (file.getSize() > MAX_VIDEO_FILE_SIZE) {
			throw new ArynBusinessException("上传视频不能超过100MB");
		}

		String extension = extractExtension(file);
		String contentType = file.getContentType();
		if (!ALLOWED_VIDEO_EXTENSIONS.contains(extension)
				|| (StringUtils.hasText(contentType) && !contentType.startsWith("video/")
						&& !"application/octet-stream".equalsIgnoreCase(contentType))) {
			throw unsupportedVideo();
		}

		if (!matchesVideoExtension(readHeader(file), extension)) {
			throw unsupportedVideo();
		}
	}

	private void checkNotEmpty(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ArynBusinessException("上传文件不能为空");
		}
	}

	private String extractExtension(MultipartFile file) {
		return FileUtil.extName(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
	}

	private byte[] readHeader(MultipartFile file) {
		try (InputStream inputStream = file.getInputStream()) {
			return inputStream.readNBytes(12);
		}
		catch (IOException exception) {
			throw new ArynBusinessException("读取上传文件失败");
		}
	}

	private boolean matchesImageExtension(byte[] header, String extension) {
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

	private boolean matchesVideoExtension(byte[] header, String extension) {
		return switch (extension) {
			// ISO BMFF（MP4/MOV/M4V）：第 5-8 字节为 "ftyp" box 头
			case "mp4", "mov", "m4v" -> startsWithAt(header, 4, 0x66, 0x74, 0x79, 0x70);
			// Matroska/WebM：EBML 魔数
			case "webm" -> startsWith(header, 0x1A, 0x45, 0xDF, 0xA3);
			default -> false;
		};
	}

	private boolean startsWith(byte[] actual, int... expected) {
		return startsWithAt(actual, 0, expected);
	}

	private boolean startsWithAt(byte[] actual, int offset, int... expected) {
		if (actual.length < offset + expected.length) {
			return false;
		}
		for (int index = 0; index < expected.length; index++) {
			if (Byte.toUnsignedInt(actual[offset + index]) != expected[index]) {
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

	private ArynBusinessException unsupportedImage() {
		return new ArynBusinessException("仅支持 JPG、PNG、GIF、BMP、WEBP 图片");
	}

	private ArynBusinessException unsupportedVideo() {
		return new ArynBusinessException("仅支持 MP4、MOV、M4V、WEBM 视频");
	}

}
