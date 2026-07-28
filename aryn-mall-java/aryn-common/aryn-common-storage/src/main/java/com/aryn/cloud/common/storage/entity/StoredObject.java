package com.aryn.cloud.common.storage.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 上传完成后的对象键与兼容访问地址。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoredObject {

	private String objectKey;
	private String url;

}
