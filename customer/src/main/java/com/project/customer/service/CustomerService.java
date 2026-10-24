package com.project.customer.service;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.AuthUser;
import com.project.common.security.CurrentUser;
import com.project.customer.dto.CustomerDtos.CreateCustomerRequest;
import com.project.customer.dto.CustomerDtos.UpdateCustomerRequest;
import com.project.customer.entity.Customer;
import com.project.customer.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

/**
 * Logic nghiệp vụ customer.
 * <ul>
 * <li>REST (người dùng cuối): chỉ chủ hồ sơ hoặc ADMIN được xem/sửa; danh sách chỉ ADMIN.</li>
 * <li>gRPC (service nội bộ): dùng các method không kiểm tra sở hữu ({@link #getById}, {@link #findByIds}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    public static final int MAX_BATCH_SIZE = 100;

    private final CustomerRepository customerRepository;

    /** Không kiểm tra sở hữu: chỉ dùng cho gRPC nội bộ. */
    public Customer getById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Customer id=" + id + " không tồn tại"));
    }

    /** Không kiểm tra sở hữu: chỉ dùng cho gRPC nội bộ. */
    public Customer getByEmail(String email) {
        return customerRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Customer email=" + email + " không tồn tại"));
    }

    /** Không kiểm tra sở hữu: chỉ dùng cho gRPC nội bộ. */
    public List<Customer> findByIds(Collection<Long> ids) {
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "Tối đa " + MAX_BATCH_SIZE + " id mỗi lần gọi");
        }
        return ids.isEmpty() ? List.of() : customerRepository.findAllById(ids);
    }

    public Customer getMine() {
        AuthUser user = CurrentUser.require();
        return customerRepository.findByUserId(user.id())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Bạn chưa tạo hồ sơ customer"));
    }

    public Customer getForCurrentUser(Long id) {
        Customer customer = getById(id);
        requireOwnerOrAdmin(customer, CurrentUser.require());
        return customer;
    }

    public Page<Customer> list(Pageable pageable) {
        CurrentUser.requireAdmin();
        return customerRepository.findAll(pageable);
    }

    @Transactional
    public Customer create(CreateCustomerRequest req) {
        AuthUser user = CurrentUser.require();
        requireText(req.fullName(), "fullName");
        if (!StringUtils.hasText(user.email())) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "Token không có email");
        }
        String email = normalizeEmail(user.email());
        if (customerRepository.existsByUserId(user.id())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Bạn đã có hồ sơ customer");
        }
        if (customerRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Email " + email + " đã được sử dụng");
        }

        Customer customer = new Customer();
        customer.setUserId(user.id());
        customer.setFullName(req.fullName().trim());
        customer.setEmail(email);
        customer.setPhone(emptyToNull(req.phone()));
        try {
            // saveAndFlush để lỗi unique constraint (cùng user/email tạo đồng thời) nổ ra tại đây
            return customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Bạn đã có hồ sơ customer");
        }
    }

    @Transactional
    public Customer update(Long id, UpdateCustomerRequest req) {
        AuthUser user = CurrentUser.require();
        requireText(req.fullName(), "fullName");
        Customer customer = getById(id);
        requireOwnerOrAdmin(customer, user);
        if (req.active() != customer.isActive() && !user.isAdmin()) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ ADMIN được khoá / mở khoá customer");
        }
        if (!customer.getVersion().equals(req.version())) {
            throw new AppException(ErrorCode.VERSION_CONFLICT,
                    "Customer đã bị thay đổi bởi request khác (version hiện tại=" + customer.getVersion() + ")");
        }
        customer.setFullName(req.fullName().trim());
        customer.setPhone(emptyToNull(req.phone()));
        customer.setActive(req.active());
        // Nếu request khác commit giữa lúc đọc và ghi, @Version sẽ ném OptimisticLockingFailureException
        return customerRepository.saveAndFlush(customer);
    }

    private static void requireOwnerOrAdmin(Customer customer, AuthUser user) {
        if (!user.isAdmin() && !user.id().equals(customer.getUserId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền với customer id=" + customer.getId());
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
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
