package com.aryn.cloud.generator.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.generator.entity.GenDataSource;

public interface IGenDataSourceService extends IService<GenDataSource> {

	/**
	 * 新增数据源
	 * @param genDataSource
	 * @return
	 */
	boolean saveDataSource(GenDataSource genDataSource);

	/**
	 * 修改数据源
	 * @param genDataSource
	 * @return
	 */
	boolean updateDataSourceById(GenDataSource genDataSource);

}
