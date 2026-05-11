package com.aryn.cloud.generator.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.generator.entity.GenTableColumn;
import com.aryn.cloud.generator.mapper.GenTableColumnMapper;
import com.aryn.cloud.generator.service.IGenTableColumnService;
import org.springframework.stereotype.Service;

@Service
public class GenTableColumnServiceImpl extends ServiceImpl<GenTableColumnMapper, GenTableColumn>
		implements IGenTableColumnService {

}
