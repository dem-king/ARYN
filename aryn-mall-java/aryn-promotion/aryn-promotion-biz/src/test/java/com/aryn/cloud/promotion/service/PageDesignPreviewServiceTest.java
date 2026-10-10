package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import com.aryn.cloud.promotion.mapper.PageDesignReleaseMapper;
import com.aryn.cloud.promotion.mapper.PageDesignReleaseTargetMapper;
import com.aryn.cloud.promotion.mapper.PageDesignVersionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageDesignPreviewServiceTest {

	@Mock
	private PageDesignMapper pageDesignMapper;

	@Mock
	private PageDesignVersionMapper versionMapper;

	@Mock
	private PageDesignReleaseMapper releaseMapper;

	@Mock
	private PageDesignReleaseTargetMapper releaseTargetMapper;

	@Mock
	private StringRedisTemplate redisTemplate;

	private PageDesignPreviewService service;

	@BeforeAll
	static void initPageDesignTableInfo() {
		// LambdaQueryWrapper 渲染列名依赖 TableInfo 缓存，纯单测环境需手动初始化
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PageDesign.class);
	}

	@BeforeEach
	void setUp() {
		service = new PageDesignPreviewService(pageDesignMapper, versionMapper, releaseMapper, releaseTargetMapper,
				redisTemplate);
	}

	@Test
	void findEffectivePageOrdersByHomeFlagThenLatestPublish() {
		List<Wrapper<PageDesign>> captured = new ArrayList<>();
		when(pageDesignMapper.selectOne(any())).thenAnswer(invocation -> {
			captured.add(invocation.getArgument(0));
			return null;
		});

		service.findEffectivePage("3");

		// 同类型存在多条已发布页面时 C 端读取结果必须确定：首页标记优先、最近发布优先
		String sqlSegment = captured.get(0).getSqlSegment();
		assertTrue(sqlSegment.contains("home_status DESC"), sqlSegment);
		assertTrue(sqlSegment.contains("published_at DESC"), sqlSegment);
		assertTrue(sqlSegment.toLowerCase().contains("limit 1"), sqlSegment);
	}

	@Test
	void getPublishedByTypeReturnsNullInsteadOfErrorWhenTypeNotDecorated() {
		when(pageDesignMapper.selectOne(any())).thenReturn(null);

		assertNull(service.getPublishedByType("3"));
	}

}
