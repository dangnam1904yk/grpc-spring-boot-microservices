package com.project.category.catalog;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.project.common.exception.FieldErrors;

/**
 * REST CRUD + search dùng chung. Controller con khai báo {@code @RestController @RequestMapping(...)} và
 * {@link #toResponse}; API riêng của danh mục thì viết thêm method trong controller con.
 * Body không dùng {@code @Valid}: {@link CatalogService} kiểm tra (cả annotation) để gom đủ lỗi một lần.
 * <pre>
 * GET    {prefix}?keyword=&amp;active=&amp;page=0&amp;size=20&amp;sort=name,desc&amp;&lt;filter riêng&gt;=
 * GET    {prefix}/{code}
 * POST   {prefix}
 * PUT    {prefix}/{id}
 * DELETE {prefix}/{id}?version=
 * </pre>
 */
public abstract class CatalogController<E extends CatalogEntity, Q extends CatalogRequest, R> {

    private static final int MAX_PAGE_SIZE = 100;

    /** Param chung của search, không chuyển sang filter riêng. */
    private static final Set<String> COMMON_PARAMS = Set.of("keyword", "active", "page", "size", "sort");

    protected final CatalogService<E, Q> service;

    protected CatalogController(CatalogService<E, Q> service) {
        this.service = service;
    }

    protected abstract R toResponse(E entity);

    @GetMapping
    public PageResponse<R> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam Map<String, String> params) {
        Map<String, String> filters = new HashMap<>(params);
        filters.keySet().removeAll(COMMON_PARAMS);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                parseSort(sort));
        return PageResponse.from(service.search(keyword, active, filters, pageable).map(this::toResponse));
    }

    @GetMapping("/{code}")
    public R getByCode(@PathVariable String code) {
        return toResponse(service.getByCode(code));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public R create(@RequestBody Q req) {
        return toResponse(service.create(req));
    }

    @PutMapping("/{id}")
    public R update(@PathVariable UUID id, @RequestBody Q req) {
        return toResponse(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam Long version) {
        service.delete(id, version);
    }

    /** {@code "name"} hoặc {@code "name,desc"}; thêm id làm tiêu chí phụ để phân trang ổn định. */
    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String field = parts.length == 0 ? "" : parts[0].trim();
        if (field.isEmpty() || parts.length > 2) {
            throw FieldErrors.of("sort", "catalog.sort.invalid", sort);
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> FieldErrors.of("sort", "catalog.sort.invalid", sort));
        }
        Sort result = Sort.by(direction, field);
        return "id".equals(field) ? result : result.and(Sort.by("id"));
    }
}
