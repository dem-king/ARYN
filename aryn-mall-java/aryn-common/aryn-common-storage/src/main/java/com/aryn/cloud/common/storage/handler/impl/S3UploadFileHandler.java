/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 * 仅供购买并获得正式授权的客户使用，侵权必究。
 */

package com.aryn.cloud.common.storage.handler.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.storage.entity.StorageConfig;
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

/**
 * S3协议上传文件处理器
 *
 * @author 雨滴kian
 */
@Component
public class S3UploadFileHandler extends AbstractUploadFileHandler {

	@SneakyThrows
	@Override
	public String doUploadFile(StorageConfig storageConfig, InputStream inputStream, String fileName, long size) {
		String rawEndpoint = storageConfig.getEndpoint();

		// 使用 AWS S3 SDK 构建客户端
		// 为了最大的兼容性 (包括 MinIO, Ceph, RustFS 等)，默认启用 Path Style Access
		S3Client s3Client = S3Client.builder()
			.endpointOverride(URI.create(getDomain(rawEndpoint)))
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(storageConfig.getAccessKeyId(), storageConfig.getAccessKeySecret())))
			.region(Region.US_EAST_1) // 大多数 S3 兼容服务忽略 Region，但 SDK 需要一个值
			.serviceConfiguration(S3Configuration.builder()
				.pathStyleAccessEnabled(storageConfig.getStyleAccessEnabled())
				.chunkedEncodingEnabled(false) // 一些 S3 兼容服务对 chunked 支持不完整
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

			// 拼接返回 URL
			// 注意：有些服务可能返回的 URL 需要特殊处理，这里采用通用的 Endpoint + Bucket + ObjectKey 方式
			// 如果 Endpoint 包含 bucket (Virtual Host 风格)，拼接方式会不同，但这里强制了 Path Style
			// 如果 Endpoint 结尾带有 /，处理一下
			String baseUrl = getDomain(rawEndpoint);
			if (baseUrl.endsWith("/")) {
				baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
			}

			// 配置了自定义域名时，优先使用自定义域名返回文件访问地址
			String customDomain = storageConfig.getDomain();
			if (StringUtils.hasText(customDomain)) {
				String customBaseUrl = getDomain(customDomain);
				if (customBaseUrl.endsWith("/")) {
					customBaseUrl = customBaseUrl.substring(0, customBaseUrl.length() - 1);
				}
				return customBaseUrl + "/" + objectName;
			}

			String endpointLower = rawEndpoint.toLowerCase();
			if (endpointLower.contains("oss") || endpointLower.contains("cos") || endpointLower.contains("qiniucs")) {
				return "https://" + bucketName + "." + storageConfig.getEndpoint() + "/" + objectName;
			}
			return baseUrl + "/" + bucketName + "/" + objectName;
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

	private String getDomain(String endpoint) {
		// 自动补全协议头
		if (!endpoint.startsWith("http")) {
			// 默认使用 HTTPS，除非明确指定了 HTTP（对于本地测试或内部网络）
			endpoint = "https://" + endpoint;
		}
		return endpoint;
	}

}
