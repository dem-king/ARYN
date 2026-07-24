
package com.aryn.cloud.common.storage.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件存储配置
 *
 * @author 雨滴kian
 * @date 2022/6/10
 */
@Data
public class StorageConfig {

	private String type;

	/** AccessKeyId */
	private String accessKeyId;

	/** AccessKeySecret。 */
	private String accessKeySecret;

	/** endpoint。 */
	private String endpoint;

	/** bucketname。 */
	private String bucket;

	/** 指定文件夹 */
	private String dir;

	private String contextType;

	@Schema(description = "domain 自定义域名")
	private String domain;

	@Schema(description = "是否 path-style")
	private Boolean styleAccessEnabled;

}
