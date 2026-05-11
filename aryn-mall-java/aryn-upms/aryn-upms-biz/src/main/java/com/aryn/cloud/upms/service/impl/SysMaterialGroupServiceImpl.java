
package com.aryn.cloud.upms.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.upms.api.entity.SysMaterialGroup;
import com.aryn.cloud.upms.mapper.SysMaterialGroupMapper;
import com.aryn.cloud.upms.service.ISysMaterialGroupService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 素材分组
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:37
 */
@Service
public class SysMaterialGroupServiceImpl extends ServiceImpl<SysMaterialGroupMapper, SysMaterialGroup>
		implements ISysMaterialGroupService {

	@Override
	public IPage<SysMaterialGroup> getPage(Page page, SysMaterialGroup sysMaterialGroup) {
		return baseMapper.selectMaterialGroupPage(page, sysMaterialGroup);
	}

	@Override
	public List<SysMaterialGroup> getList(SysMaterialGroup sysMaterialGroup) {
		return baseMapper.selectMaterialGroupList(sysMaterialGroup);
	}

}
