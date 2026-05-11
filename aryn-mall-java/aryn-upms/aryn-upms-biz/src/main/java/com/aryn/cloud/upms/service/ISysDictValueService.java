
package com.aryn.cloud.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.upms.api.entity.SysDictValue;

/**
 * 字典键值
 *
 * @author 雨滴kian
 * @date 2022/10/21
 */
public interface ISysDictValueService extends IService<SysDictValue> {

	/**
	 * 新增字典值
	 * @param sysDictValue
	 * @return
	 */
	boolean saveDictValue(SysDictValue sysDictValue);

}
