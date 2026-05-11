
package com.aryn.cloud.product.service;

import cn.hutool.core.lang.tree.Tree;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.product.api.entity.GoodsCategory;

import java.util.List;

/**
 * 商品类目
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:32
 */
public interface IGoodsCategoryService extends IService<GoodsCategory> {

	/**
	 * 商品类目树形结构
	 *
	 * @author 雨滴kian
	 * @date 2022/6/16
	 * @return: java.util.List<cn.hutool.core.lang.tree.Tree<java.lang.String>>
	 */
	List<Tree<String>> getGoodsCategoryTreeList();

}
