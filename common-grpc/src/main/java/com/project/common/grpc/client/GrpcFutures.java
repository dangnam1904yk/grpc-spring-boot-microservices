package com.project.common.grpc.client;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.MDC;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.project.common.audit.AuditContext;

public final class GrpcFutures {

    private GrpcFutures() {
    }

    /**
     * Chuyển ListenableFuture của gRPC FutureStub sang CompletableFuture; huỷ future sẽ huỷ luôn gRPC call.
     * Callback chạy trên thread của gRPC nhưng vẫn mang MDC (request id, user) của thread gọi,
     * để các bước {@code thenApply...} sau đó ghi log / thay đổi dữ liệu vẫn gắn đúng request.
     */
    public static <T> CompletableFuture<T> toCompletableFuture(ListenableFuture<T> listenableFuture) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        CompletableFuture<T> future = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                listenableFuture.cancel(mayInterruptIfRunning);
                return super.cancel(mayInterruptIfRunning);
            }
        };
        Futures.addCallback(listenableFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(T result) {
                AuditContext.runWith(mdc, () -> future.complete(result));
            }

            @Override
            public void onFailure(Throwable t) {
                AuditContext.runWith(mdc, () -> future.completeExceptionally(t));
            }
        }, MoreExecutors.directExecutor());
        return future;
    }
}
