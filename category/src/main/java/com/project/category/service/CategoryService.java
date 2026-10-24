package com.project.category.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.project.category.dto.CategoryDtos.CreateCategoryRequest;
import com.project.category.dto.CategoryDtos.UpdateCategoryRequest;
import com.project.category.entity.Category;
import com.project.category.repository.CategoryRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.CurrentUser;

import lombok.RequiredArgsConstructor;

/**
 * Logic nghiệp vụ category. REST controller và gRPC service đều gọi vào đây.
 * Phân quyền: đọc thì ai cũng được; thêm/sửa chỉ ADMIN (kiểm tra ở đây nên áp dụng cho cả REST lẫn gRPC).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    public static final int MAX_BATCH_SIZE = 100;

    private final CategoryRepository categoryRepository;

    public Category getById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Category id=" + id + " không tồn tại"));
    }

    public Category getByCode(String code) {
        return categoryRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Category code=" + code + " không tồn tại"));
    }

    public List<Category> findByCodes(Collection<String> codes) {
        if (codes.size() > MAX_BATCH_SIZE) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "Tối đa " + MAX_BATCH_SIZE + " code mỗi lần gọi");
        }
        return codes.isEmpty() ? List.of() : categoryRepository.findByCodeIn(codes);
    }

    public Page<Category> list(String parentCode, Pageable pageable) {
        return StringUtils.hasText(parentCode)
                ? categoryRepository.findByParentCode(parentCode, pageable)
                : categoryRepository.findAll(pageable);
    }

    @Transactional
    public Category create(CreateCategoryRequest req) {
        CurrentUser.requireAdmin();
        requireText(req.code(), "code");
        requireText(req.name(), "name");
        if (categoryRepository.existsByCode(req.code())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Category code=" + req.code() + " đã tồn tại");
        }
        validateParent(req.parentCode(), req.code());

        Category category = new Category();
        category.setCode(req.code());
        category.setName(req.name());
        category.setDescription(req.description());
        category.setParentCode(emptyToNull(req.parentCode()));
        try {
            // saveAndFlush để lỗi unique constraint (2 request tạo cùng code đồng thời) nổ ra tại đây
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Category code=" + req.code() + " đã tồn tại");
        }
    }

    @Transactional
    public Category update(UUID id, UpdateCategoryRequest req) {
        CurrentUser.requireAdmin();
        requireText(req.name(), "name");
        Category category = getById(id);
        if (!category.getVersion().equals(req.version())) {
            throw new AppException(ErrorCode.VERSION_CONFLICT,
                    "Category đã bị thay đổi bởi request khác (version hiện tại=" + category.getVersion() + ")");
        }
        validateParent(req.parentCode(), category.getCode());

        category.setName(req.name());
        category.setDescription(req.description());
        category.setParentCode(emptyToNull(req.parentCode()));
        category.setActive(req.active());
        // Nếu request khác commit giữa lúc đọc và ghi, @Version sẽ ném OptimisticLockingFailureException
        return categoryRepository.saveAndFlush(category);
    }

    private void validateParent(String parentCode, String selfCode) {
        if (!StringUtils.hasText(parentCode)) {
            return;
        }
        if (parentCode.equals(selfCode)) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "Category không thể là cha của chính nó");
        }
        if (!categoryRepository.existsByCode(parentCode)) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "Parent code=" + parentCode + " không tồn tại");
        }
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, field + " không được để trống");
        }
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
