package com.aryn.cloud.product.mapper;

import com.aryn.cloud.product.api.entity.GoodsPriceHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品价格变更历史 Mapper。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Mapper
public interface GoodsPriceHistoryMapper extends BaseMapper<GoodsPriceHistory> {

}
