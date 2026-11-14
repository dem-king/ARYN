package com.aryn.cloud.user.mapper;

import com.aryn.cloud.user.api.entity.EnterpriseLevel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** EnterpriseLevel 持久层（企业会员等级）。 */
@Mapper
public interface EnterpriseLevelMapper extends BaseMapper<EnterpriseLevel> {

}
