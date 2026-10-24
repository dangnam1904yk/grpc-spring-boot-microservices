package com.project.business.service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.project.business.client.CategoryGrpcClient;
import com.project.business.client.CustomerGrpcClient;
import com.project.business.dto.BusinessDtos.BusinessResponse;
import com.project.business.dto.BusinessDtos.CategoryInfo;
import com.project.business.dto.BusinessDtos.CreateBusinessRequest;
import com.project.business.dto.BusinessDtos.CustomerInfo;
import com.project.business.dto.BusinessDtos.PageResponse;
import com.project.business.entity.Business;
import com.project.business.repository.BusinessRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.AuthUser;
import com.project.common.security.CurrentUser;
import com.project.grpc.category.v1.Category;
import com.project.grpc.customer.v1.Customer;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;

/**
 * Các method trả về CompletableFuture: Tomcat thread được trả về pool trong lúc chờ gRPC,
 * nên cùng số thread phục vụ được nhiều request đồng thời hơn.
 * Lời gọi sang category-service và customer-service luôn chạy song song.
 */
@Slf4j
@Service
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final CategoryGrpcClient categoryClient;
    private final CustomerGrpcClient customerClient;
    private final Executor dbExecutor;

    public BusinessService(BusinessRepository businessRepository, CategoryGrpcClient categoryClient,
            CustomerGrpcClient customerClient, @Qualifier("applicationTaskExecutor") Executor dbExecutor) {
        this.businessRepository = businessRepository;
        this.categoryClient = categoryClient;
        this.customerClient = customerClient;
        this.dbExecutor = dbExecutor;
    }

    /**
     * Chỉ được tạo business cho hồ sơ customer của chính mình (ADMIN tạo được cho bất kỳ ai).
     */
    public CompletableFuture<BusinessResponse> create(CreateBusinessRequest req) {
        // Lấy user ngay trên thread của request: SecurityContext không đi theo sang thenApplyAsync
        AuthUser user = CurrentUser.require();
        // Kiểm tra sớm để khỏi tốn lời gọi gRPC; unique constraint ở DB vẫn là chốt chặn cuối
        if (businessRepository.existsByCode(req.code())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Business code=" + req.code() + " đã tồn tại");
        }
        // Gửi 2 lời gọi cùng lúc: tổng thời gian = lời gọi chậm nhất, không phải tổng 2 lời gọi
        CompletableFuture<Category> category = categoryClient.getByCode(req.categoryCode())
                .exceptionally(ex -> {
                    throw translateLookupError(ex, "Category code=" + req.categoryCode());
                });
        CompletableFuture<Customer> owner = customerClient.getById(req.ownerId())
                .exceptionally(ex -> {
                    throw translateLookupError(ex, "Customer id=" + req.ownerId());
                });

        return category.thenCombine(owner, Lookup::new)
                // Ghi DB là blocking: chạy trên pool riêng, không chạy trên thread callback của gRPC
                .thenApplyAsync(lookup -> {
                    if (!user.isAdmin() && !user.id().equals(lookup.owner().getUserId())) {
                        throw new AppException(ErrorCode.FORBIDDEN,
                                "Chỉ được tạo business cho hồ sơ customer của chính mình");
                    }
                    if (!lookup.category().getActive()) {
                        throw new AppException(ErrorCode.INVALID_ARGUMENT,
                                "Category code=" + req.categoryCode() + " đang ngưng hoạt động");
                    }
                    if (!lookup.owner().getActive()) {
                        throw new AppException(ErrorCode.INVALID_ARGUMENT,
                                "Customer id=" + req.ownerId() + " đang bị khoá");
                    }
                    return BusinessResponse.of(saveNew(req), CategoryInfo.from(lookup.category()),
                            CustomerInfo.from(lookup.owner()));
                }, dbExecutor);
    }

    public CompletableFuture<BusinessResponse> get(Long id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Business id=" + id + " không tồn tại"));

        // Đọc dữ liệu: service nào lỗi thì chỉ thiếu phần của service đó, vẫn trả business
        CompletableFuture<Category> category = categoryClient.getByCode(business.getCategoryCode())
                .exceptionally(ex -> degrade("category " + business.getCategoryCode(), ex, null));
        CompletableFuture<Customer> owner = customerClient.getById(business.getOwnerId())
                .exceptionally(ex -> degrade("customer " + business.getOwnerId(), ex, null));

        return category.thenCombine(owner,
                (c, o) -> BusinessResponse.of(business, CategoryInfo.from(c), CustomerInfo.from(o)));
    }

    public CompletableFuture<PageResponse<BusinessResponse>> list(Pageable pageable) {
        var page = businessRepository.findAll(pageable);
        Set<String> categoryCodes = page.stream()
                .map(Business::getCategoryCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> ownerIds = page.stream()
                .map(Business::getOwnerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Mỗi service một lời gọi batch cho cả trang (thay vì N lời gọi), 2 batch chạy song song
        CompletableFuture<Map<String, Category>> categories = categoryClient.batchGetByCodes(categoryCodes)
                .exceptionally(ex -> degrade("danh sách category", ex, Map.of()));
        CompletableFuture<Map<Long, Customer>> owners = customerClient.batchGetByIds(ownerIds)
                .exceptionally(ex -> degrade("danh sách customer", ex, Map.of()));

        return categories.thenCombine(owners, (categoryMap, ownerMap) -> PageResponse.from(page.map(b ->
                BusinessResponse.of(b,
                        CategoryInfo.from(categoryMap.get(b.getCategoryCode())),
                        CustomerInfo.from(ownerMap.get(b.getOwnerId()))))));
    }

    private Business saveNew(CreateBusinessRequest req) {
        Business business = new Business();
        business.setCode(req.code());
        business.setName(req.name());
        business.setCategoryCode(req.categoryCode());
        business.setOwnerId(req.ownerId());
        try {
            return businessRepository.saveAndFlush(business);
        } catch (DataIntegrityViolationException e) {
            // Hai request cùng code chạy song song: request đến sau bị unique constraint chặn
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Business code=" + req.code() + " đã tồn tại");
        }
    }

    private static <T> T degrade(String what, Throwable ex, T fallback) {
        log.warn("Không lấy được {}: {}", what, unwrap(ex).getMessage());
        return fallback;
    }

    /** NOT_FOUND từ service khác = dữ liệu đầu vào sai (400); lỗi khác giữ nguyên để map sang 503/504. */
    private static RuntimeException translateLookupError(Throwable ex, String what) {
        Throwable cause = unwrap(ex);
        if (cause instanceof StatusRuntimeException sre && sre.getStatus().getCode() == Status.Code.NOT_FOUND) {
            return new AppException(ErrorCode.INVALID_ARGUMENT, what + " không tồn tại");
        }
        return ex instanceof CompletionException ce ? ce : new CompletionException(cause);
    }

    private static Throwable unwrap(Throwable ex) {
        return ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
    }

    private record Lookup(Category category, Customer owner) {
    }
}
