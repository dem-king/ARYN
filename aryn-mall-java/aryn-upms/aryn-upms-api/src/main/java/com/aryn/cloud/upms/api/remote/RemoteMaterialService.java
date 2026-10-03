package com.aryn.cloud.upms.api.remote;

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

}
