/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 * 仅供购买并获得正式授权的客户使用，侵权必究。
 */

package com.aryn.cloud.common.storage.handler.impl;

import com.aryn.cloud.common.core.constant.StorageTypeConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.storage.entity.StorageConfig;
import com.aryn.cloud.common.storage.entity.StoredObject;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S3协议上传文件处理器
 *
 * @author 雨滴kian
 */
@Component
public class S3UploadFileHandler extends AbstractUploadFileHandler {

	private static final Pattern ALIYUN_REGION_PATTERN = Pattern.compile("^oss-([^.]+)\\.");

	private static final Pattern TENCENT_REGION_PATTERN = Pattern.compile("^cos\\.([^.]+)\\.");

	private static final Pattern QINIU_REGION_PATTERN = Pattern.compile("^s3-([^.]+)\\.");

	@SneakyThrows
	@Override
	public StoredObject doUploadObject(StorageConfig storageConfig, InputStream inputStream, String fileName,
			long size) {
		URI endpoint = resolveEndpoint(storageConfig.getEndpoint());
		boolean pathStyleAccessEnabled = Boolean.TRUE.equals(storageConfig.getStyleAccessEnabled())
				|| StorageTypeConstants.MINIO.equals(StorageTypeConstants.normalize(storageConfig.getType()));
		S3Client s3Client = S3Client.builder()
			.endpointOverride(endpoint)
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(storageConfig.getAccessKeyId(), storageConfig.getAccessKeySecret())))
			.region(resolveRegion(storageConfig.getType(), endpoint.getHost()))
			.serviceConfiguration(S3Configuration.builder()
				.pathStyleAccessEnabled(pathStyleAccessEnabled)
				.chunkedEncodingEnabled(false)
				.build())
			.build();

		try {
			String bucketName = storageConfig.getBucket();
			// 获取后缀
			String suffix = "";
			if (fileName.contains(".")) {
				suffix = fileName.substring(fileName.lastIndexOf(".") + 1);
			}

			String dir = storageConfig.getDir();
			String uuidFileName = UUID.randomUUID() + (StringUtils.hasText(suffix) ? "." + suffix : "");
			String objectName = ArynTenantContextHolder.getTenantId() + (StringUtils.hasText(dir) ? ("/" + dir) : "")
					+ "/" + uuidFileName;

			PutObjectRequest putObjectRequest = PutObjectRequest.builder()
				.bucket(bucketName)
				.key(objectName)
				.contentType(storageConfig.getContextType())
				.build();

			// 使用流上传，并指定长度
			s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, size));

			return new StoredObject(objectName,
				buildPublicUrl(storageConfig, endpoint, objectName, pathStyleAccessEnabled));
		}
		finally {
			// 确保资源释放
			s3Client.close();
		}
	}

	@Override
	public String getType() {
		return "oss";
	}

	static URI resolveEndpoint(String endpoint) {
		String normalizedEndpoint = endpoint.trim();
		if (!normalizedEndpoint.startsWith("http://") && !normalizedEndpoint.startsWith("https://")) {
			normalizedEndpoint = "https://" + normalizedEndpoint;
		}
		return URI.create(normalizedEndpoint);
	}

	static Region resolveRegion(String type, String host) {
		String normalizedType = StorageTypeConstants.normalize(type);
		Pattern pattern = switch (normalizedType) {
			case StorageTypeConstants.ALIYUN -> ALIYUN_REGION_PATTERN;
			case StorageTypeConstants.TENCENT -> TENCENT_REGION_PATTERN;
			case StorageTypeConstants.QINIU -> QINIU_REGION_PATTERN;
			default -> null;
		};
		if (pattern != null && host != null) {
			Matcher matcher = pattern.matcher(host.toLowerCase());
			if (matcher.find()) {
				return Region.of(matcher.group(1));
			}
		}
		return Region.US_EAST_1;
	}

	static String buildPublicUrl(StorageConfig storageConfig, URI endpoint, String objectName,
			boolean pathStyleAccessEnabled) {
		if (StringUtils.hasText(storageConfig.getDomain())) {
			return trimTrailingSlash(storageConfig.getDomain()) + "/" + objectName;
		}
		String endpointBase = trimTrailingSlash(endpoint.toString());
		if (pathStyleAccessEnabled) {
			return endpointBase + "/" + storageConfig.getBucket() + "/" + objectName;
		}
		String authority = storageConfig.getBucket() + "." + endpoint.getHost()
				+ (endpoint.getPort() < 0 ? "" : ":" + endpoint.getPort());
		return endpoint.getScheme() + "://" + authority + "/" + objectName;
	}

	private static String trimTrailingSlash(String value) {
		String normalized = value.trim();
		return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
	}

}
