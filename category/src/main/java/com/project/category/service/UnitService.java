package com.project.category.service;

import java.util.Map;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.project.category.catalog.CatalogPermissions;
import com.project.category.catalog.CatalogService;
import com.project.category.dto.UnitDtos.UnitRequest;
import com.project.category.entity.Unit;
import com.project.category.repository.UnitRepository;
import com.project.common.exception.FieldErrors;
import com.project.common.security.Permissions;

@Service
public class UnitService extends CatalogService<Unit, UnitRequest> {

    public UnitService(UnitRepository unitRepository) {
        super(unitRepository, "catalog.label.unit", new CatalogPermissions(
                Permissions.UNIT_CREATE, Permissions.UNIT_UPDATE, Permissions.UNIT_DELETE));
    }

    @Override
    protected Unit newEntity() {
        return new Unit();
    }

    @Override
    protected void validate(UnitRequest req, Unit current, FieldErrors errors) {
        maxLength(errors, "symbol", req.symbol(), Unit.SYMBOL_LENGTH);
    }

    @Override
    protected void applyExtra(Unit unit, UnitRequest req) {
        unit.setSymbol(emptyToNull(req.symbol()));
    }

    /** {@code GET ...?symbol=kg} */
    @Override
    protected Specification<Unit> extraFilter(Map<String, String> filters) {
        String symbol = filters.get("symbol");
        return StringUtils.hasText(symbol) ? (root, query, cb) -> cb.equal(root.get("symbol"), symbol) : null;
    }

    @Override
    protected Set<String> extraSortFields() {
        return Set.of("symbol");
    }
}
