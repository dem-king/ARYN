
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
	 * 商品类目树形结构（管理端口径）
	 *
	 * <p>{@code extra.badgeType} 保留数据库原始值（未配置为 null、停用/未知 key 原样回传），
	 * 供管理端类目列表/表单回显「引用了停用角标」的类目；{@code extra.badge} 仅在命中启用角标时
	 * 为 {@code {text,bgColor,color}} 对象，否则为 null。
	 *
	 * @author 雨滴kian
	 * @date 2022/6/16
	 * @return: java.util.List<cn.hutool.core.lang.tree.Tree<java.lang.String>>
	 */
	List<Tree<String>> getGoodsCategoryTreeList();

	/**
	 * 商品类目树形结构（App/C 端口径）
	 *
	 * <p>未命中启用角标（未配置/停用/已删除/未知 key）时 {@code extra.badgeType} 归一为 {@code '0'}、
	 * {@code extra.badge} 为 null；命中时保留原始 key 并下发 {@code {text,bgColor,color}}。
	 * C 端据此只渲染配置命中的角标，无需内置「荐绿/热红」映射。
	 *
	 * @return 归一后的类目树
	 */
	List<Tree<String>> getAppGoodsCategoryTreeList();

}
