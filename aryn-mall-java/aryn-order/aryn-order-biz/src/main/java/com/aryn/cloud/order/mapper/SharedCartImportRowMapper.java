package com.aryn.cloud.order.mapper;

import com.aryn.cloud.order.api.entity.SharedCartImportRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 补给单 Excel 导入解析行持久层。 */
@Mapper
public interface SharedCartImportRowMapper extends BaseMapper<SharedCartImportRow> {
}
