package com.aryn.cloud.order.mapper;

import com.aryn.cloud.order.api.entity.DeliveryQualificationOperation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配送资格操作（可靠授权/回收记录）
 *
 * @author aryn
 * @since 2026/9/6
 */
@Mapper
public interface DeliveryQualificationOperationMapper extends BaseMapper<DeliveryQualificationOperation> {

}
