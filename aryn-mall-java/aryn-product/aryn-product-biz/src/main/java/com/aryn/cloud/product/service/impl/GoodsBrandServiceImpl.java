package com.aryn.cloud.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.product.api.entity.GoodsBrand;
import com.aryn.cloud.product.mapper.GoodsBrandMapper;
import com.aryn.cloud.product.service.IGoodsBrandService;
import org.springframework.stereotype.Service;

@Service
public class GoodsBrandServiceImpl extends ServiceImpl<GoodsBrandMapper, GoodsBrand> implements IGoodsBrandService {
}
