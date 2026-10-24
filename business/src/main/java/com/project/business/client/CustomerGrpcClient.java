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

import com.project.grpc.customer.v1.BatchGetCustomersRequest;
import com.project.grpc.customer.v1.BatchGetCustomersResponse;
import com.project.grpc.customer.v1.Customer;
import com.project.grpc.customer.v1.CustomerServiceGrpc;
import com.project.grpc.customer.v1.GetCustomerRequest;

import net.devh.boot.grpc.client.inject.GrpcClient;

/**
 * Client gọi customer-service qua gRPC (non-blocking, có deadline), cùng cách làm với {@link CategoryGrpcClient}.
 */
@Component
public class CustomerGrpcClient {

    // Khớp CustomerService.MAX_BATCH_SIZE bên customer-service
    private static final int MAX_BATCH_SIZE = 100;

    @GrpcClient("customer-service")
    private CustomerServiceGrpc.CustomerServiceFutureStub stub;

    private final Duration deadline;

    public CustomerGrpcClient(@Value("${app.grpc.customer.deadline:2s}") Duration deadline) {
        this.deadline = deadline;
    }

    public CompletableFuture<Customer> getById(long id) {
        return toCompletableFuture(withDeadline().getCustomer(GetCustomerRequest.newBuilder().setId(id).build()));
    }

    /** Trả về map id -> customer (id không tồn tại sẽ không có trong map). Danh sách lớn được chia lô gọi song song. */
    public CompletableFuture<Map<Long, Customer>> batchGetByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        List<Long> all = new ArrayList<>(ids);
        List<CompletableFuture<BatchGetCustomersResponse>> calls = new ArrayList<>();
        for (int i = 0; i < all.size(); i += MAX_BATCH_SIZE) {
            List<Long> chunk = all.subList(i, Math.min(i + MAX_BATCH_SIZE, all.size()));
            calls.add(toCompletableFuture(withDeadline().batchGetCustomers(
                    BatchGetCustomersRequest.newBuilder().addAllIds(chunk).build())));
        }
        return CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    Map<Long, Customer> result = new HashMap<>();
                    calls.forEach(call -> call.join().getCustomersList()
                            .forEach(c -> result.put(c.getId(), c)));
                    return result;
                });
    }

    private CustomerServiceGrpc.CustomerServiceFutureStub withDeadline() {
        return stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS);
    }
}
