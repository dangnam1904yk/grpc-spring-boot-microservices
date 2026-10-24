package com.project.common.grpc.client;

import java.util.concurrent.CompletableFuture;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

public final class GrpcFutures {

    private GrpcFutures() {
    }

    /** Chuyển ListenableFuture của gRPC FutureStub sang CompletableFuture; huỷ future sẽ huỷ luôn gRPC call. */
    public static <T> CompletableFuture<T> toCompletableFuture(ListenableFuture<T> listenableFuture) {
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
                future.complete(result);
            }

            @Override
            public void onFailure(Throwable t) {
                future.completeExceptionally(t);
            }
        }, MoreExecutors.directExecutor());
        return future;
    }
}
