
package com.aryn.cloud.upms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;
import com.aryn.cloud.upms.api.entity.SysStorageConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件存储配置
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:50
 */
@Mapper
public interface SysStorageConfigMapper extends BaseMapper<SysStorageConfig> {

	/**
	 * 查询文件配置
	 *
	 * @author 雨滴kian
	 * @date 2022/9/20
	 * @return: com.aryn.cloud.common.core.dto.SysStorageConfigDTO
	 */
	SysStorageConfigDTO selectConfig();

}
