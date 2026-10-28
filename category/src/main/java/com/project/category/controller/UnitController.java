package com.project.category.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.category.catalog.CatalogController;
import com.project.category.dto.UnitDtos.UnitRequest;
import com.project.category.dto.UnitDtos.UnitResponse;
import com.project.category.entity.Unit;
import com.project.category.service.UnitService;
import com.project.category.ultis.Constants;

@RestController
@RequestMapping(Constants.ENDPOINT.PREFIX_UNIT)
public class UnitController extends CatalogController<Unit, UnitRequest, UnitResponse> {

    public UnitController(UnitService unitService) {
        super(unitService);
    }

    @Override
    protected UnitResponse toResponse(Unit unit) {
        return UnitResponse.from(unit);
    }
}
