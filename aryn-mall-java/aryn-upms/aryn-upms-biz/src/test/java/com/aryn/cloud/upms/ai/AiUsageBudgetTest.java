package com.aryn.cloud.upms.ai;
import com.aryn.cloud.upms.api.dto.ai.*;
import com.aryn.cloud.upms.api.entity.ai.*;
import com.aryn.cloud.upms.config.AiCredentialProperties;
import com.aryn.cloud.upms.service.impl.AiUsageBudgetService;
import com.aryn.cloud.upms.mapper.*;
import com.aryn.cloud.common.ai.contract.*;
import com.aryn.cloud.common.ai.error.*;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AiUsageBudgetTest {
    AiUsageBudgetMapper budgets;AiCallLogMapper logs;AiUsageBudgetService service;AiCredentialProperties settings;
    AiRuntimeRequest request;AiModelSnapshot model;Map<String,AiCallLog> rows;
    @BeforeEach void setup() {
        ArynTenantContextHolder.setTenantId("t");budgets=mock(AiUsageBudgetMapper.class);logs=mock(AiCallLogMapper.class);
        when(budgets.selectById(any())).thenReturn(new AiUsageBudget());when(budgets.selectOne(any())).thenReturn(new AiUsageBudget());
        settings=new AiCredentialProperties();service=new AiUsageBudgetService(budgets,logs,settings);rows=new HashMap<>();
        when(logs.selectById(any())).thenAnswer(i->rows.get(i.getArgument(0)));
        when(logs.selectCount(any())).thenReturn(0L);when(logs.selectList(any())).thenAnswer(i->new ArrayList<>(rows.values()));
        doAnswer(i->{var row=(AiCallLog)i.getArgument(0);rows.put(row.getId(),row);return 1;}).when(logs).insert(any(AiCallLog.class));
        request=new AiRuntimeRequest(new AiCallContext("c","task","t",AiUsage.PROCUREMENT_PARSE),"order",0,"","v","p",0);
        model=new AiModelSnapshot("v","key","p",URI.create("https://example.com"),"m",AiCapability.STRUCTURED_TEXT,1000,1000,100,null,Map.of(),new AiSecret("secret"));
    }
    @AfterEach void cleanup(){ArynTenantContextHolder.removeTenantId();}
    @Test void repeatedBeginAndUsageReportAreIdempotent() {
        assertTrue(service.begin(request,model));assertFalse(service.begin(request,model));
        var report=new AiUsageReport(request,"SUCCESS",12L,100);
        service.report(report);service.report(new AiUsageReport(request,"SUCCESS",99L,100));
        assertEquals(12L,rows.values().iterator().next().getTotalTokens());verify(logs,times(1)).updateById(any(AiCallLog.class));
    }
    @Test void unknownTokensStayUnknownAndExhaustedBudgetRejectsBeforeCall() {
        assertTrue(service.begin(request,model));service.report(new AiUsageReport(request,"TIMEOUT",null,100));
        assertNull(rows.values().iterator().next().getTotalTokens());
        var another=new AiRuntimeRequest(new AiCallContext("c2","task","t",AiUsage.PROCUREMENT_PARSE),"order",0,"","v","p",0);
        settings.setDailyTokenLimit(4100);assertThrows(AiCallException.class,()->service.begin(another,model));
        assertEquals(1,rows.size());
    }
    @Test void concurrencyTaskLimitsAndTenantAreEnforced() {
        when(logs.selectCount(any())).thenReturn(4L);
        assertThrows(AiCallException.class,()->service.begin(request,model));verify(logs,never()).insert(any(AiCallLog.class));
        ArynTenantContextHolder.setTenantId("other");assertThrows(SecurityException.class,()->service.begin(request,model));
    }
}
