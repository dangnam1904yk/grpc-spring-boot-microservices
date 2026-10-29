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
import com.project.common.exception.FieldErrors;
import com.project.common.i18n.LocalizedMessage;
import com.project.common.security.CurrentUser;

/**
 * Logic CRUD + search dùng chung cho mọi danh mục. REST controller và gRPC service đều gọi vào đây nên phân quyền,
 * kiểm tra dữ liệu áp dụng cho cả hai.
 * <p>
 * Danh mục con khai báo {@code @Service} kế thừa class này, cài {@link #newEntity()} và override các hook khi cần:
 * {@link #validate}, {@link #applyExtra}, {@link #extraFilter}, {@link #extraSortFields}, {@link #beforeDelete}.
 * Nghiệp vụ riêng thì viết thêm method public trong service con.
 * <p>
 * Kiểm tra dữ liệu: field chung ({@link #validateCommon}) + {@link #validate} của danh mục con được gom vào
 * {@link FieldErrors}, trả về một lần dạng field -&gt; message (400 / INVALID_ARGUMENT). Kiểm tra ở đây chứ không
 * ở {@code @Valid} của controller để gRPC cũng được kiểm tra.
 */
@Transactional(readOnly = true)
public abstract class CatalogService<E extends CatalogEntity, Q extends CatalogRequest> {

    public static final int MAX_BATCH_SIZE = 100;

    /** Key chung của common-grpc. */
    public static final String REQUIRED = "error.required";

    /** Field được phép sort; danh mục con bổ sung qua {@link #extraSortFields()}. */
    private static final Set<String> BASE_SORT_FIELDS =
            Set.of("id", "code", "name", "sortOrder", "active", "createDate", "lastModifyDate");

    protected final CatalogRepository<E> repository;
    /** Tên danh mục (đã dịch theo ngôn ngữ của request), tham số {0} của message lỗi. */
    protected final LocalizedMessage label;
    private final CatalogPermissions permissions;

    /** @param labelKey key tên danh mục trong i18n/messages_xx.properties, vd. {@code catalog.label.unit} */
    protected CatalogService(CatalogRepository<E> repository, String labelKey, CatalogPermissions permissions) {
        this.repository = repository;
        this.label = LocalizedMessage.of(labelKey);
        this.permissions = permissions;
    }

    // ---------------------------------------------------------------- hook cho danh mục con

    protected abstract E newEntity();

    /**
     * Kiểm tra field riêng của danh mục: ghi lỗi vào {@code errors} ({@code errors.add("parentCode", "...")},
     * {@link #maxLength}...) thay vì ném, để trả cùng lỗi chung. {@code current} = null khi tạo mới.
     */
    protected void validate(Q req, E current, FieldErrors errors) {
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
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "catalog.not-found.id", label, id));
    }

    public E getByCode(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "catalog.not-found.code", label, code));
    }

    public List<E> findByCodes(Collection<String> codes) {
        if (codes.size() > MAX_BATCH_SIZE) {
            throw FieldErrors.of("codes", "catalog.batch.too-many", MAX_BATCH_SIZE);
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
        FieldErrors errors = validateCommon(req, true);
        validate(req, null, errors);
        errors.throwIfAny();
        if (repository.existsByCode(req.code())) {
            throw alreadyExists(req.code());
        }

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
        E entity = getById(id);
        FieldErrors errors = validateCommon(req, false);
        validate(req, entity, errors);
        errors.throwIfAny();
        checkVersion(entity, req.version());

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

    // ---------------------------------------------------------------- kiểm tra dữ liệu

    /** Field chung: tạo mới thì bắt buộc code (không sửa được nên update bỏ qua), sửa thì bắt buộc version. */
    private FieldErrors validateCommon(Q req, boolean creating) {
        FieldErrors errors = FieldErrors.create();
        if (creating) {
            requireText(errors, "code", req.code());
            maxLength(errors, "code", req.code(), CatalogEntity.CODE_LENGTH);
        } else if (req.version() == null) {
            errors.add("version", REQUIRED);
        }
        requireText(errors, "name", req.name());
        maxLength(errors, "name", req.name(), CatalogEntity.NAME_LENGTH);
        maxLength(errors, "description", req.description(), CatalogEntity.DESCRIPTION_LENGTH);
        return errors;
    }

    protected static void requireText(FieldErrors errors, String field, String value) {
        if (!StringUtils.hasText(value)) {
            errors.add(field, REQUIRED);
        }
    }

    protected static void maxLength(FieldErrors errors, String field, String value, int max) {
        if (value != null && value.length() > max) {
            errors.add(field, "catalog.too-long", max);
        }
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
            throw FieldErrors.of("version", REQUIRED);
        }
        if (!entity.getVersion().equals(version)) {
            throw new AppException(ErrorCode.VERSION_CONFLICT, "catalog.version-conflict", label,
                    String.valueOf(entity.getVersion()));
        }
    }

    private void checkSort(Sort sort) {
        for (Sort.Order order : sort) {
            String field = order.getProperty();
            if (!BASE_SORT_FIELDS.contains(field) && !extraSortFields().contains(field)) {
                throw FieldErrors.of("sort", "catalog.sort.unsupported", field);
            }
        }
    }

    private AppException alreadyExists(String code) {
        return new AppException(ErrorCode.ALREADY_EXISTS, LocalizedMessage.of("catalog.already-exists", label, code),
                Map.of("code", LocalizedMessage.of("catalog.code.exists")));
    }

    protected static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
