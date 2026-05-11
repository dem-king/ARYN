
package com.aryn.cloud.upms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.upms.api.entity.SysTenantPackage;
import com.aryn.cloud.upms.mapper.SysTenantPackageMapper;
import com.aryn.cloud.upms.service.ISysTenantPackageService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户套餐
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:51
 */
@Service
@AllArgsConstructor
public class SysTenantPackageServiceImpl extends ServiceImpl<SysTenantPackageMapper, SysTenantPackage>
		implements ISysTenantPackageService {

}
