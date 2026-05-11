
package com.aryn.cloud.upms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.upms.api.entity.SysLog;
import com.aryn.cloud.upms.mapper.SysLogMapper;
import com.aryn.cloud.upms.service.ISysLogService;
import org.springframework.stereotype.Service;

/**
 * 操作日志
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
@Service
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLog> implements ISysLogService {

}
