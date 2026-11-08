package com.aryn.cloud.upms.api.remote;

import com.aryn.cloud.upms.api.vo.RemoteImageSizeVO;

import java.util.List;
import java.util.Map;

/**
 * 素材远程查询服务
 *
 * @author aryn
 * @since 2026/10/2
 */
public interface RemoteMaterialService {

	/**
	 * 按素材 ID 批量查询访问 URL（已删除或无 URL 的素材不出现在结果中）
	 * @param materialIds 素材 ID 列表
	 * @return 素材ID -> 访问URL
	 */
	Map<String, String> mapUrlByIds(List<String> materialIds);

	/**
	 * 为私有素材 URL 追加限时访问签名（保持入参顺序）；公开素材原样返回。
	 * @param urls 素材访问 URL
	 * @return 签名后的 URL
	 */
	List<String> signPrivateUrls(List<String> urls);

	/**
	 * 批量解析图片像素宽高（商品卡占位比例用）。
	 *
	 * <p>只解析受信任来源：本地上传文件直接读磁盘，其它 URL 仅允许与当前租户
	 * 文件存储域名同源；解析失败的 URL 不出现在结果中，调用方按 1:1 兜底。
	 * 结果带进程内缓存，重复调用不会重复读文件/网络。</p>
	 * @param urls 图片 URL（按当前租户上下文解析）
	 * @return 图片URL -> 像素宽高
	 */
	Map<String, RemoteImageSizeVO> resolveImageSizes(List<String> urls);

}
