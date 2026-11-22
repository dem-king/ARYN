package com.aryn.cloud.product.support;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.entity.*;
import com.aryn.cloud.product.mapper.*;
import com.aryn.cloud.product.service.impl.SkuIdentifierService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SkuIdentifierServiceTest {
    SkuIdentifierMapper mapper; GoodsSkuMapper skus; SkuIdentifierService service;
    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SkuIdentifier.class);
        ArynTenantContextHolder.setTenantId("tenant");
        mapper = mock(SkuIdentifierMapper.class); skus = mock(GoodsSkuMapper.class);
        service = new SkuIdentifierService(mapper, skus);
    }
    @AfterEach void cleanup() { ArynTenantContextHolder.removeTenantId(); }
    @Test void importCreatesPendingMappingAndApprovalRequiresFreshRevisionAndExistingSku() {
        when(skus.selectById("sku")).thenReturn(new GoodsSku().setId("sku"));
        var row = service.register(List.of(new SkuIdentifierService.Input("sku", "ALIAS", " 土 豆 ", "IMPORT")), "admin").get(0);
        assertEquals("PENDING", row.getStatus()); assertNull(row.getReviewBy()); assertEquals("土豆", row.getNormalizedValue());
        row.setId("map"); when(mapper.selectById("map")).thenReturn(row); when(mapper.updateById(row)).thenReturn(1);
        assertThrows(ArynBusinessException.class, () -> service.review("map", 1, true, "reviewer"));
        service.review("map", 0, true, "reviewer"); assertEquals("APPROVED", row.getStatus()); assertEquals("reviewer", row.getReviewBy());
        when(skus.selectById("sku")).thenReturn(null);
        assertThrows(ArynBusinessException.class, () -> service.review("map", row.getRevision(), false, "reviewer"));
    }
    @Test void conflictingCodesAndUnauthorizedSkuAreRejectedWithoutNewMapping() {
        when(skus.selectById("sku")).thenReturn(new GoodsSku().setId("sku"));
        when(mapper.selectCount(any())).thenReturn(1L);
        assertThrows(ArynBusinessException.class, () -> service.register(List.of(new SkuIdentifierService.Input("sku", "IMPA", "123456", "MANUAL")), "admin"));
        verify(mapper, never()).insert(any(SkuIdentifier.class));
        assertThrows(ArynBusinessException.class, () -> service.register(List.of(new SkuIdentifierService.Input("foreign", "ISSA", "123456", "MANUAL")), "admin"));
        assertNotEquals(SkuIdentifierService.normalized("ALIAS", "abc"), SkuIdentifierService.normalized("CUSTOM", "abc"));
    }
}
