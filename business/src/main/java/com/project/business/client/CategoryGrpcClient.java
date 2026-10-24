package com.project.business.client;

import static com.project.common.grpc.client.GrpcFutures.toCompletableFuture;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.project.grpc.category.v1.BatchGetCategoriesRequest;
import com.project.grpc.category.v1.BatchGetCategoriesResponse;
import com.project.grpc.category.v1.Category;
import com.project.grpc.category.v1.CategoryServiceGrpc;
import com.project.grpc.category.v1.GetCategoryRequest;

import net.devh.boot.grpc.client.inject.GrpcClient;

/**
 * Client gọi category-service qua gRPC.
 * <ul>
 * <li>Dùng FutureStub (non-blocking): thread không bị chiếm trong lúc chờ mạng.</li>
 * <li>Mỗi call có deadline: category chậm/treo thì fail nhanh, không kéo sập business.</li>
 * <li>Lỗi trả về là {@link io.grpc.StatusRuntimeException} trong CompletableFuture.</li>
 * </ul>
 */
@Component
public class CategoryGrpcClient {

    // Khớp CategoryService.MAX_BATCH_SIZE bên category-service
    private static final int MAX_BATCH_SIZE = 100;

    @GrpcClient("category-service")
    private CategoryServiceGrpc.CategoryServiceFutureStub stub;

    private final Duration deadline;

    public CategoryGrpcClient(@Value("${app.grpc.category.deadline:2s}") Duration deadline) {
        this.deadline = deadline;
    }

    public CompletableFuture<Category> getByCode(String code) {
        return toCompletableFuture(withDeadline().getCategory(
                GetCategoryRequest.newBuilder().setCode(code).build()));
    }

    /**
     * Lấy nhiều category theo code, trả về map code -> category (code không tồn tại sẽ không có trong map).
     * Danh sách lớn được chia lô và gọi song song.
     */
    public CompletableFuture<Map<String, Category>> batchGetByCodes(Collection<String> codes) {
        if (codes.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        List<String> all = new ArrayList<>(codes);
        List<CompletableFuture<BatchGetCategoriesResponse>> calls = new ArrayList<>();
        for (int i = 0; i < all.size(); i += MAX_BATCH_SIZE) {
            List<String> chunk = all.subList(i, Math.min(i + MAX_BATCH_SIZE, all.size()));
            calls.add(toCompletableFuture(withDeadline().batchGetCategories(
                    BatchGetCategoriesRequest.newBuilder().addAllCodes(chunk).build())));
        }
        return CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    Map<String, Category> result = new HashMap<>();
                    calls.forEach(call -> call.join().getCategoriesList()
                            .forEach(c -> result.put(c.getCode(), c)));
                    return result;
                });
    }

    private CategoryServiceGrpc.CategoryServiceFutureStub withDeadline() {
        // Deadline tính từ lúc gọi, nên phải tạo mới cho mỗi request
        return stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS);
    }
}
