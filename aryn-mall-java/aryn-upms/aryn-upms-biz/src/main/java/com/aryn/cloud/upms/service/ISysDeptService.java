
package com.aryn.cloud.upms.service;

import cn.hutool.core.lang.tree.Tree;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.upms.api.entity.SysDept;

import java.util.List;

/**
 * 部门
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
public interface ISysDeptService extends IService<SysDept> {

	List<Tree<String>> getTreeList();

}
