
package com.aryn.cloud.upms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.upms.api.entity.SysLogisticsCompany;
import com.aryn.cloud.upms.mapper.SysLogisticsCompanyMapper;
import com.aryn.cloud.upms.service.ISysLogisticsCompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 物流公司
 *
 * @author 雨滴kian
 * @since 2023/1/10
 */
@Service
@RequiredArgsConstructor
public class SysLogisticsCompanyServiceImpl extends ServiceImpl<SysLogisticsCompanyMapper, SysLogisticsCompany>
		implements ISysLogisticsCompanyService {

}
