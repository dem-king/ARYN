package com.aryn.cloud.order.mapper;

import com.aryn.cloud.order.api.entity.SharedCartImport;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 补给单 Excel 导入任务持久层。 */
@Mapper
public interface SharedCartImportMapper extends BaseMapper<SharedCartImport> {
}
