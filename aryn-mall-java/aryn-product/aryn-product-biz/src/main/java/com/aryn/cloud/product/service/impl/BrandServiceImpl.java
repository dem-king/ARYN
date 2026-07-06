
package com.aryn.cloud.product.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.product.api.entity.Brand;
import com.aryn.cloud.product.mapper.BrandMapper;
import com.aryn.cloud.product.service.IBrandService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品品牌
 *
 * @author aryn
 * @since 2026/7/5
 */
@Service
public class BrandServiceImpl extends ServiceImpl<BrandMapper, Brand> implements IBrandService {

	@Override
	public List<Brand> listAppBrands() {
		return list(Wrappers.<Brand>lambdaQuery()
			.eq(Brand::getStatus, "1")
			.orderByAsc(Brand::getSort)
			.select(Brand::getId, Brand::getName, Brand::getLogo, Brand::getFirstLetter, Brand::getSort));
	}

}