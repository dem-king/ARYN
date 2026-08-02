
package com.aryn.cloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配送任务
 *
 * @author aryn
 * @since 2025/7/31
 */
@Mapper
public interface DeliveryTaskMapper extends BaseMapper<DeliveryTask> {

}