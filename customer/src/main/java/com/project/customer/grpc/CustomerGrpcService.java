package com.project.customer.grpc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.project.customer.entity.Customer;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.customer.service.CustomerService;
import com.project.grpc.customer.v1.BatchGetCustomersRequest;
import com.project.grpc.customer.v1.BatchGetCustomersResponse;
import com.project.grpc.customer.v1.CustomerServiceGrpc;
import com.project.grpc.customer.v1.GetCustomerRequest;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * gRPC adapter cho service khác gọi vào. Chỉ chuyển đổi proto <-> entity, logic nằm ở {@link CustomerService}.
 * Lỗi ném ra được GrpcExceptionAdvice (module common-grpc) chuyển thành gRPC Status.
 */
@GrpcService
@RequiredArgsConstructor
public class CustomerGrpcService extends CustomerServiceGrpc.CustomerServiceImplBase {

    private final CustomerService customerService;

    @Override
    public void getCustomer(GetCustomerRequest request,
            StreamObserver<com.project.grpc.customer.v1.Customer> responseObserver) {
        Customer customer = switch (request.getKeyCase()) {
            case ID -> customerService.getById(request.getId());
            case EMAIL -> customerService.getByEmail(request.getEmail());
            case KEY_NOT_SET -> throw new AppException(ErrorCode.INVALID_ARGUMENT, "Phải truyền id hoặc email");
        };
        responseObserver.onNext(toProto(customer));
        responseObserver.onCompleted();
    }

    @Override
    public void batchGetCustomers(BatchGetCustomersRequest request,
            StreamObserver<BatchGetCustomersResponse> responseObserver) {
        Set<Long> ids = new LinkedHashSet<>(request.getIdsList());
        List<Customer> found = customerService.findByIds(ids);
        Set<Long> foundIds = found.stream().map(Customer::getId).collect(Collectors.toSet());

        BatchGetCustomersResponse.Builder builder = BatchGetCustomersResponse.newBuilder();
        found.forEach(c -> builder.addCustomers(toProto(c)));
        ids.stream().filter(id -> !foundIds.contains(id)).forEach(builder::addMissingIds);
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    private static com.project.grpc.customer.v1.Customer toProto(Customer c) {
        com.project.grpc.customer.v1.Customer.Builder builder = com.project.grpc.customer.v1.Customer.newBuilder()
                .setId(c.getId())
                .setFullName(c.getFullName())
                .setEmail(c.getEmail())
                .setActive(c.isActive())
                .setVersion(c.getVersion());
        if (c.getPhone() != null) {
            builder.setPhone(c.getPhone());
        }
        if (c.getUserId() != null) {
            builder.setUserId(c.getUserId());
        }
        return builder.build();
    }
}
