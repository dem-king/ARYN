
package com.aryn.cloud.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.product.api.entity.GoodsCollect;
import com.aryn.cloud.product.api.vo.GoodsCollectVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GoodsCollectMapper extends BaseMapper<GoodsCollect> {

	IPage<GoodsCollectVO> selectCollectPage(Page page, @Param("query") GoodsCollect userCollect);

}
