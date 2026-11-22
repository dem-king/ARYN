package com.aryn.cloud.order.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.SharedCartImportConfirmDTO;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.mapper.*;
import com.aryn.cloud.order.service.impl.SharedCartServiceImpl;
import com.aryn.cloud.order.support.procurement.ProcurementContributionLedger;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 历史只读边界；新写入/归属/并发/贡献替换由 ProcurementPersistenceTest 验证。 */
class SharedCartImportServiceTest {
    private SharedCartMapper carts;
    private SharedCartMemberMapper members;
    private SharedCartItemMapper items;
    private SharedCartImportMapper imports;
    private SharedCartImportRowMapper rows;
    private SharedCartServiceImpl service;

    @BeforeEach
    void setup() {
        for (Class<?> entity : List.of(SharedCart.class, SharedCartMember.class, SharedCartImport.class, SharedCartImportRow.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entity);
        }
        carts = mock(SharedCartMapper.class);
        members = mock(SharedCartMemberMapper.class);
        items = mock(SharedCartItemMapper.class);
        imports = mock(SharedCartImportMapper.class);
        rows = mock(SharedCartImportRowMapper.class);
        service = new SharedCartServiceImpl(carts, members, items, imports, rows,
                mock(IOrderInfoService.class), mock(IDeliveryAreaService.class), mock(IBuyerOrgService.class),
                mock(SharedCartInviteNotifyService.class), mock(ProcurementContributionLedger.class));
        SharedCart cart = new SharedCart();
        cart.setId("cart"); cart.setTenantId("tenant"); cart.setOwnerUserId("owner");
        cart.setStatus(SharedCart.STATUS_COMPLETED);
        when(carts.selectOne(any(Wrapper.class))).thenReturn(cart);
    }

    @Test
    void synchronousWritesCannotBypassProcurementReview() {
        assertThrows(ArynBusinessException.class, () -> service.previewImport("tenant", "owner", "cart", "a.xlsx", 0, new byte[0]));
        assertThrows(ArynBusinessException.class, () -> service.previewChainImport("tenant", "owner", "cart", "张三 苹果3斤"));
        assertThrows(ArynBusinessException.class, () -> service.confirmImport("tenant", "owner", "cart", "import", new SharedCartImportConfirmDTO()));
        verifyNoInteractions(carts, members, items, imports, rows);
    }

    @Test
    void historicalRowsRemainReadableWithoutCurrentProductOrAi() {
        SharedCartImport job = new SharedCartImport();
        job.setId("import"); job.setCartId("cart"); job.setStatus(SharedCartImport.STATUS_IMPORTED);
        SharedCartImportRow row = new SharedCartImportRow();
        row.setRowNo(3); row.setPersonName("张三"); row.setRawName("苹果");
        row.setRawUnit("斤"); row.setRawQuantity(3); row.setResolvedQuantity(2);
        row.setResolvedSkuId("sku"); row.setResolvedAction("ACCEPT_SPEC");
        when(imports.selectOne(any(Wrapper.class))).thenReturn(job);
        when(rows.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        var report = service.getImport("tenant", "owner", "cart", "import");
        assertEquals("import", report.getImportId());
        assertEquals("张三", report.getRows().get(0).getPersonName());
        assertEquals("斤", report.getRows().get(0).getRawUnit());
        assertEquals(2, report.getRows().get(0).getResolvedQuantity());
        verify(items, never()).insert(any(SharedCartItem.class));
        verify(imports, never()).updateById(any(SharedCartImport.class));
        verify(rows, never()).updateById(any(SharedCartImportRow.class));
    }

    @Test
    void historicalReportsAndListRejectNonMembersBeforeLoadingContent() {
        when(members.selectCount(any(Wrapper.class))).thenReturn(0L);
        assertThrows(ArynBusinessException.class, () -> service.getImport("tenant", "stranger", "cart", "import"));
        assertThrows(ArynBusinessException.class, () -> service.listImports("tenant", "stranger", "cart"));
        verifyNoInteractions(imports, rows, items);
    }

    @Test
    void reportListDoesNotFetchRowsAndIsScopedToTenantAndCart() {
        when(imports.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().contains("tenant_id"));
            assertTrue(query.getSqlSegment().contains("cart_id"));
            SharedCartImport job = new SharedCartImport(); job.setId("import");
            return List.of(job);
        });
        assertEquals("import", service.listImports("tenant", "owner", "cart").get(0).getImportId());
        verifyNoInteractions(rows, items);
    }
}
