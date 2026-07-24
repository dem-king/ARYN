package com.aryn.cloud.common.core.constant;

import java.util.Map;
import java.util.Set;

public interface StorageTypeConstants {

	String LOCAL = "local";

	String ALIYUN = "aliyun";

	String QINIU = "qiniu";

	String TENCENT = "tencent";

	String MINIO = "minio";

	String LEGACY_OSS = "oss";

	Set<String> OBJECT_STORAGE_TYPES = Set.of(ALIYUN, QINIU, TENCENT, MINIO, LEGACY_OSS);

	Map<String, String> LEGACY_TYPES = Map.of("1", ALIYUN, "2", TENCENT, "3", QINIU, "4", MINIO);

	static String normalize(String type) {
		if (type == null) {
			return null;
		}
		String trimmedType = type.trim().toLowerCase();
		return LEGACY_TYPES.getOrDefault(trimmedType, trimmedType);
	}

	static boolean isObjectStorage(String type) {
		return OBJECT_STORAGE_TYPES.contains(normalize(type));
	}

	static boolean isSupported(String type) {
		String normalizedType = normalize(type);
		return LOCAL.equals(normalizedType) || OBJECT_STORAGE_TYPES.contains(normalizedType);
	}

}
