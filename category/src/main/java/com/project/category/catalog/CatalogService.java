package com.project.category.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.CurrentUser;

/**
 * Logic CRUD + search dùng chung cho mọi danh mục. REST controller và gRPC service đều gọi vào đây nên phân quyền,
 * kiểm tra dữ liệu áp dụng cho cả hai.
 * <p>
 * Danh mục con khai báo {@code @Service} kế thừa class này, cài {@link #newEntity()} và override các hook khi cần:
 * {@link #validate}, {@link #applyExtra}, {@link #extraFilter}, {@link #extraSortFields}, {@link #beforeDelete}.
 * Nghiệp vụ riêng thì viết thêm method public trong service con.
 */
@Transactional(readOnly = true)
public abstract class CatalogService<E extends CatalogEntity, Q extends CatalogRequest> {

    public static final int MAX_BATCH_SIZE = 100;

    /** Field được phép sort; danh mục con bổ sung qua {@link #extraSortFields()}. */
    private static final Set<String> BASE_SORT_FIELDS =
            Set.of("id", "code", "name", "sortOrder", "active", "createDate", "lastModifyDate");

    protected final CatalogRepository<E> repository;
    /** Tên danh mục, dùng trong thông báo lỗi. */
    protected final String label;
    private final CatalogPermissions permissions;

    protected CatalogService(CatalogRepository<E> repository, String label, CatalogPermissions permissions) {
        this.repository = repository;
        this.label = label;
        this.permissions = permissions;
    }

    // ---------------------------------------------------------------- hook cho danh mục con

    protected abstract E newEntity();

    /** Kiểm tra riêng của danh mục, chạy sau kiểm tra chung. {@code current} = null khi tạo mới. */
    protected void validate(Q req, E current) {
    }

    /** Copy field riêng từ request vào entity (cả lúc tạo và sửa). */
    protected void applyExtra(E entity, Q req) {
    }

    /**
     * Điều kiện lọc riêng từ query param (ngoài keyword / active / page / size / sort). Chỉ đọc những param đã biết,
     * không đưa tên param thẳng vào {@code root.get(...)}. Trả null nếu không lọc gì thêm.
     */
    protected Specification<E> extraFilter(Map<String, String> filters) {
        return null;
    }

    protected Set<String> extraSortFields() {
        return Set.of();
    }

    /** Chặn xoá khi dữ liệu khác đang tham chiếu: ném {@code AppException(ErrorCode.IN_USE, ...)}. */
    protected void beforeDelete(E entity) {
    }

    // ---------------------------------------------------------------- đọc

    public E getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, label + " id=" + id + " không tồn tại"));
    }

    public E getByCode(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, label + " code=" + code + " không tồn tại"));
    }

    public List<E> findByCodes(Collection<String> codes) {
        if (codes.size() > MAX_BATCH_SIZE) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "Tối đa " + MAX_BATCH_SIZE + " code mỗi lần gọi");
        }
        return codes.isEmpty() ? List.of() : repository.findByCodeIn(codes);
    }

    /**
     * @param keyword tìm gần đúng theo code hoặc name, không phân biệt hoa thường
     * @param active  null = cả hai
     * @param filters param lọc riêng, xử lý ở {@link #extraFilter}
     */
    public Page<E> search(String keyword, Boolean active, Map<String, String> filters, Pageable pageable) {
        checkSort(pageable.getSort());
        List<Specification<E>> specs = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), pattern, '\\'),
                    cb.like(cb.lower(root.get("name")), pattern, '\\')));
        }
        if (active != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        Specification<E> extra = extraFilter(filters);
        if (extra != null) {
            specs.add(extra);
        }
        return repository.findAll(Specification.allOf(specs), pageable);
    }

    // ---------------------------------------------------------------- ghi

    @Transactional
    public E create(Q req) {
        CurrentUser.requirePermission(permissions.create());
        requireText(req.code(), "code");
        requireText(req.name(), "name");
        if (repository.existsByCode(req.code())) {
            throw alreadyExists(req.code());
        }
        validate(req, null);

        E entity = newEntity();
        entity.setCode(req.code());
        entity.setActive(req.active() == null || req.active());
        copyCommon(entity, req);
        applyExtra(entity, req);
        try {
            // saveAndFlush để lỗi unique constraint (2 request tạo cùng code đồng thời) nổ ra tại đây
            return repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw alreadyExists(req.code());
        }
    }

    @Transactional
    public E update(UUID id, Q req) {
        CurrentUser.requirePermission(permissions.update());
        requireText(req.name(), "name");
        E entity = getById(id);
        checkVersion(entity, req.version());
        validate(req, entity);

        if (req.active() != null) {
            entity.setActive(req.active());
        }
        copyCommon(entity, req);
        applyExtra(entity, req);
        // Nếu request khác commit giữa lúc đọc và ghi, @Version sẽ ném OptimisticLockingFailureException
        return repository.saveAndFlush(entity);
    }

    /**
     * Xoá hẳn. Service khác tham chiếu danh mục bằng code (không có FK) nên DB không chặn được: muốn ngừng dùng
     * thì nên sửa {@code active=false}; danh mục nào biết dữ liệu tham chiếu tới mình thì kiểm tra ở {@link #beforeDelete}.
     */
    @Transactional
    public void delete(UUID id, Long version) {
        CurrentUser.requirePermission(permissions.delete());
        E entity = getById(id);
        checkVersion(entity, version);
        beforeDelete(entity);
        repository.delete(entity);
        repository.flush();
    }

    // ---------------------------------------------------------------- tiện ích

    private void copyCommon(E entity, Q req) {
        entity.setName(req.name());
        entity.setDescription(emptyToNull(req.description()));
        if (req.sortOrder() != null) {
            entity.setSortOrder(req.sortOrder());
        }
    }

    private void checkVersion(E entity, Long version) {
        if (version == null) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "version không được để trống");
        }
        if (!entity.getVersion().equals(version)) {
            throw new AppException(ErrorCode.VERSION_CONFLICT,
                    label + " đã bị thay đổi bởi request khác (version hiện tại=" + entity.getVersion() + ")");
        }
    }

    private void checkSort(Sort sort) {
        for (Sort.Order order : sort) {
            String field = order.getProperty();
            if (!BASE_SORT_FIELDS.contains(field) && !extraSortFields().contains(field)) {
                throw new AppException(ErrorCode.INVALID_ARGUMENT, "Không sort được theo field " + field);
            }
        }
    }

    private AppException alreadyExists(String code) {
        return new AppException(ErrorCode.ALREADY_EXISTS, label + " code=" + code + " đã tồn tại");
    }

    protected static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, field + " không được để trống");
        }
    }

    protected static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
