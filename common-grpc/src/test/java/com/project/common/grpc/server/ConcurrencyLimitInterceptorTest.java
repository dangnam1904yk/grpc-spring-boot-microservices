package com.project.common.grpc.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.common.util.concurrent.ListenableFuture;
import com.project.grpc.category.v1.Category;
import com.project.grpc.category.v1.CategoryServiceGrpc;
import com.project.grpc.category.v1.GetCategoryRequest;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

class ConcurrencyLimitInterceptorTest {

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void rejectsCallsOverLimitAndReleasesPermitsWhenDone() throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CategoryServiceGrpc.CategoryServiceImplBase slowService = new CategoryServiceGrpc.CategoryServiceImplBase() {
            @Override
            public void getCategory(GetCategoryRequest request, StreamObserver<Category> observer) {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                observer.onNext(Category.newBuilder().setCode(request.getCode()).build());
                observer.onCompleted();
            }
        };

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .addService(ServerInterceptors.intercept(slowService, new ConcurrencyLimitInterceptor(2)))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).build();
        CategoryServiceGrpc.CategoryServiceFutureStub async = CategoryServiceGrpc.newFutureStub(channel);
        CategoryServiceGrpc.CategoryServiceBlockingStub blocking = CategoryServiceGrpc.newBlockingStub(channel);

        // 2 call chiếm hết permit
        ListenableFuture<Category> first = async.getCategory(request("A"));
        ListenableFuture<Category> second = async.getCategory(request("B"));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        // Call thứ 3 bị từ chối ngay, không phải chờ
        assertThatThrownBy(() -> blocking.withDeadlineAfter(1, TimeUnit.SECONDS).getCategory(request("C")))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(Status.Code.RESOURCE_EXHAUSTED);

        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).getCode()).isEqualTo("A");
        assertThat(second.get(5, TimeUnit.SECONDS).getCode()).isEqualTo("B");

        // Permit đã được trả lại
        assertThat(blocking.getCategory(request("D")).getCode()).isEqualTo("D");
    }

    private static GetCategoryRequest request(String code) {
        return GetCategoryRequest.newBuilder().setCode(code).build();
    }
}
