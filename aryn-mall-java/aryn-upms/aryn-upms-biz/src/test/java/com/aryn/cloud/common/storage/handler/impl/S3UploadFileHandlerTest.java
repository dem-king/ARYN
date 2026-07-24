package com.aryn.cloud.common.storage.handler.impl;

import com.aryn.cloud.common.storage.entity.StorageConfig;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class S3UploadFileHandlerTest {

	@Test
	void derivesProviderRegionsFromOfficialEndpoints() {
		assertThat(S3UploadFileHandler.resolveRegion("aliyun", "oss-cn-hangzhou.aliyuncs.com"))
			.isEqualTo(Region.of("cn-hangzhou"));
		assertThat(S3UploadFileHandler.resolveRegion("tencent", "cos.ap-guangzhou.myqcloud.com"))
			.isEqualTo(Region.of("ap-guangzhou"));
		assertThat(S3UploadFileHandler.resolveRegion("qiniu", "s3-cn-east-1.qiniucs.com"))
			.isEqualTo(Region.of("cn-east-1"));
		assertThat(S3UploadFileHandler.resolveRegion("minio", "minio.internal"))
			.isEqualTo(Region.US_EAST_1);
	}

	@Test
	void buildsPathStyleVirtualHostAndCustomDomainUrls() {
		StorageConfig config = new StorageConfig();
		config.setBucket("mall-assets");
		URI endpoint = S3UploadFileHandler.resolveEndpoint("minio.example.com:9000");

		assertThat(S3UploadFileHandler.buildPublicUrl(config, endpoint, "tenant/file.png", true))
			.isEqualTo("https://minio.example.com:9000/mall-assets/tenant/file.png");
		assertThat(S3UploadFileHandler.buildPublicUrl(config, URI.create("https://oss-cn-hangzhou.aliyuncs.com"),
				"tenant/file.png", false))
			.isEqualTo("https://mall-assets.oss-cn-hangzhou.aliyuncs.com/tenant/file.png");

		config.setDomain("https://cdn.example.com/");
		assertThat(S3UploadFileHandler.buildPublicUrl(config, endpoint, "tenant/file.png", true))
			.isEqualTo("https://cdn.example.com/tenant/file.png");
	}

}
