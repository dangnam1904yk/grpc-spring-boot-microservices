package com.project.category.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.project.category.catalog.CatalogPermissions;
import com.project.category.catalog.CatalogService;
import com.project.category.dto.CategoryDtos.CategoryRequest;
import com.project.category.entity.Category;
import com.project.category.repository.CategoryRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.Permissions;

/**
 * Category = danh mục có cây cha / con qua {@code parentCode}. CRUD + search kế thừa từ {@link CatalogService};
 * ở đây chỉ thêm phần riêng của cây.
 */
@Service
public class CategoryService extends CatalogService<Category, CategoryRequest> {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        super(categoryRepository, "Category", new CatalogPermissions(
                Permissions.CATEGORY_CREATE, Permissions.CATEGORY_UPDATE, Permissions.CATEGORY_DELETE));
        this.categoryRepository = categoryRepository;
    }

    /** Dùng cho gRPC ListCategories. */
    public Page<Category> list(String parentCode, Pageable pageable) {
        return search(null, null,
                StringUtils.hasText(parentCode) ? Map.of("parentCode", parentCode) : Map.of(), pageable);
    }

    @Override
    protected Category newEntity() {
        return new Category();
    }

    @Override
    protected void validate(CategoryRequest req, Category current) {
        String parentCode = req.parentCode();
        if (!StringUtils.hasText(parentCode)) {
            return;
        }
        String selfCode = current == null ? req.code() : current.getCode();
        if (parentCode.equals(selfCode)) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "Category không thể là cha của chính nó");
        }
        if (!categoryRepository.existsByCode(parentCode)) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "Parent code=" + parentCode + " không tồn tại");
        }
    }

    @Override
    protected void applyExtra(Category category, CategoryRequest req) {
        category.setParentCode(emptyToNull(req.parentCode()));
    }

    @Override
    protected Specification<Category> extraFilter(Map<String, String> filters) {
        String parentCode = filters.get("parentCode");
        return StringUtils.hasText(parentCode)
                ? (root, query, cb) -> cb.equal(root.get("parentCode"), parentCode)
                : null;
    }

    @Override
    protected void beforeDelete(Category category) {
        // Chỉ kiểm tra được category con; business tham chiếu category bằng code ở DB khác nên không chặn được
        if (categoryRepository.existsByParentCode(category.getCode())) {
            throw new AppException(ErrorCode.IN_USE,
                    "Category code=" + category.getCode() + " đang có category con, không xoá được");
        }
    }
}
