package com.aryn.cloud.common.ip;

/**
 * ip2region 数据目录下的固定文件名。
 *
 * <p>数据目录（{@code aryn.ip.ip2region.data-dir}）在部署上应挂持久卷：容器/实例重建后
 * 已更新的 xdb 不会丢失。文件名集中在此，provider（读取/新旧比较）与 updater（下载/备份/元数据）
 * 共用，避免两边各写一份字符串。</p>
 */
public final class IpRegionFiles {

	/** 数据文件本身（与 classpath 内置文件同名） */
	private IpRegionFiles() {
	}

	/** 下载临时文件后缀 */
	public static final String DOWNLOAD_SUFFIX = ".download";

	/** 旧数据备份后缀（保留一份，便于人工回滚） */
	public static final String BACKUP_SUFFIX = ".bak";

	/** 远端版本元数据（etag/lastModified/sha256/数据构建时间） */
	public static final String META_FILE_NAME = ".ip2region_v4.meta";

	/** 多实例刷新互斥的文件锁 */
	public static final String LOCK_FILE_NAME = ".ip2region_v4.lock";

}
