
package com.aryn.cloud.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;
import com.aryn.cloud.upms.api.entity.SysStorageConfig;

/**
 * 文件存储配置
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
public interface ISysStorageConfigService extends IService<SysStorageConfig> {

	/**
	 * 查询文件配置
	 *
	 * @author 雨滴kian
	 * @date 2022/9/20
	 * @return: com.aryn.cloud.common.core.dto.SysStorageConfigDTO
	 */
	SysStorageConfigDTO getConfig();

	/**
	 * 新增配置
	 * @param sysStorageConfig
	 * @return
	 */
	boolean saveStorageConfig(SysStorageConfig sysStorageConfig);

	/**
	 * 修改配置
	 * @param sysStorageConfig
	 * @return
	 */
	boolean updateStorageConfigById(SysStorageConfig sysStorageConfig);

}
