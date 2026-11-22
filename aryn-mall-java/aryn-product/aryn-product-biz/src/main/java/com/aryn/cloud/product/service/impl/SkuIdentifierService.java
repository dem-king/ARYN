package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.product.api.entity.SkuIdentifier;
import com.aryn.cloud.product.api.support.ReplenishMatchRules;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.SkuIdentifierMapper;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SkuIdentifierService {
    private final SkuIdentifierMapper identifiers;
    private final GoodsSkuMapper skus;
    public record Input(String skuId, String type, String value, String source) { }
    public List<SkuIdentifier> list(String skuId) {
        return identifiers.selectList(Wrappers.<SkuIdentifier>lambdaQuery().eq(SkuIdentifier::getSkuId, skuId).orderByDesc(SkuIdentifier::getCreateTime));
    }
    public static String normalized(String type, String value) {
        if (!Set.of("IMPA", "ISSA", "CUSTOM", "ALIAS").contains(Objects.toString(type, ""))) throw new ArynBusinessException("请选择IMPA/ISSA/CUSTOM/ALIAS码制");
        if (value == null || value.isBlank() || value.length() > 200 || value.chars().anyMatch(Character::isISOControl)) throw new ArynBusinessException("编码/别名不能为空且最多200字");
        return "ALIAS".equals(type) ? ReplenishMatchRules.normalizeName(value) : value.strip().toUpperCase(Locale.ROOT);
    }
    @Transactional(rollbackFor = Exception.class)
    public List<SkuIdentifier> register(List<Input> inputs, String actor) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > 500) throw new ArynBusinessException("每批支持1—500条映射");
        Set<String> keys = new HashSet<>(); List<SkuIdentifier> result = new ArrayList<>();
        for (var input : inputs) {
            String normalized = normalized(input.type(), input.value());
            if (!keys.add(input.type() + ":" + normalized)) throw new ArynBusinessException("批次内编码/别名重复");
            if (skus.selectById(input.skuId()) == null) throw new ArynBusinessException("SKU不存在或无权操作");
            if (!Set.of("MANUAL", "IMPORT").contains(Objects.toString(input.source(), ""))) throw new ArynBusinessException("映射来源无效");
            if (identifiers.selectCount(Wrappers.<SkuIdentifier>lambdaQuery().eq(SkuIdentifier::getIdentifierType, input.type()).eq(SkuIdentifier::getNormalizedValue, normalized)) > 0)
                throw new ArynBusinessException("此码制的值已有映射，请先核对现有记录");
            var row = new SkuIdentifier(); row.setSkuId(input.skuId()); row.setIdentifierType(input.type()); row.setDisplayValue(input.value().strip());
            row.setNormalizedValue(normalized); row.setStatus("PENDING"); row.setSource(input.source()); row.setRevision(0L);
            row.setTenantId(ArynTenantContextHolder.getTenantId()); row.setDelFlag("0"); row.setCreateBy(actor); row.setUpdateBy(actor);
            row.setCreateTime(LocalDateTime.now()); row.setUpdateTime(LocalDateTime.now()); identifiers.insert(row); result.add(row);
        }
        return result;
    }
    @Transactional(rollbackFor = Exception.class)
    public void review(String id, long revision, boolean approved, String actor) {
        var row = identifiers.selectById(id);
        if (row == null || row.getRevision() != revision) throw new ArynBusinessException("映射已变化或无权操作");
        if (skus.selectById(row.getSkuId()) == null) throw new ArynBusinessException("SKU已失效，不能审核");
        row.setStatus(approved ? "APPROVED" : "REJECTED"); row.setReviewBy(actor); row.setReviewTime(LocalDateTime.now());
        row.setUpdateBy(actor); row.setUpdateTime(LocalDateTime.now());
        if (identifiers.updateById(row) != 1) throw new ArynBusinessException("映射已变化，请刷新");
    }
}
