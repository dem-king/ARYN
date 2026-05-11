
package com.aryn.cloud.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.product.api.entity.GoodsSpecs;
import com.aryn.cloud.product.mapper.GoodsSpecsMapper;
import com.aryn.cloud.product.service.IGoodsSpecsService;
import org.springframework.stereotype.Service;

/**
 * 商品规格
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:37
 */
@Service
public class GoodsSpecsServiceImpl extends ServiceImpl<GoodsSpecsMapper, GoodsSpecs> implements IGoodsSpecsService {

}
