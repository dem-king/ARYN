
package com.aryn.cloud.upms.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.upms.api.entity.SysMaterial;
import com.aryn.cloud.upms.mapper.SysMaterialMapper;
import com.aryn.cloud.upms.service.ISysMaterialService;
import org.springframework.stereotype.Service;

/**
 * 素材
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:37
 */
@Service
public class SysMaterialServiceImpl extends ServiceImpl<SysMaterialMapper, SysMaterial> implements ISysMaterialService {

	@Override
	public IPage<SysMaterial> getPage(Page page, SysMaterial material) {
		return baseMapper.selectMaterialPage(page, material);
	}

}
