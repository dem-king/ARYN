
package com.aryn.cloud.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.product.api.entity.Brand;

import java.util.List;

/**
 * 商品品牌
 *
 * @author aryn
 * @since 2026/7/5
 */
public interface IBrandService extends IService<Brand> {

	/**
	 * C端品牌列表（仅启用状态）
	 *
	 * @return 品牌列表
	 */
	List<Brand> listAppBrands();

}