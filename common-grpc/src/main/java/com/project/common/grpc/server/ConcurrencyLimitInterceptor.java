package com.project.common.grpc.server;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Giới hạn số gRPC call xử lý đồng thời. Khi đầy, trả RESOURCE_EXHAUSTED ngay (load shedding)
 * thay vì để request xếp hàng vô hạn, gây hết bộ nhớ và timeout dây chuyền.
 */
public class ConcurrencyLimitInterceptor implements ServerInterceptor {

    private final Semaphore permits;

    public ConcurrencyLimitInterceptor(int maxConcurrentCalls) {
        this.permits = new Semaphore(maxConcurrentCalls);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        if (!permits.tryAcquire()) {
            call.close(Status.RESOURCE_EXHAUSTED.withDescription("Server đang quá tải, vui lòng thử lại sau"),
                    new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) {
                permits.release();
            }
        };

        ServerCall.Listener<ReqT> delegate;
        try {
            delegate = next.startCall(call, headers);
        } catch (RuntimeException e) {
            release.run();
            throw e;
        }

        // Mỗi call kết thúc bằng đúng một trong hai: onComplete hoặc onCancel (kể cả hết deadline)
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onComplete() {
                try {
                    super.onComplete();
                } finally {
                    release.run();
                }
            }

            @Override
            public void onCancel() {
                try {
                    super.onCancel();
                } finally {
                    release.run();
                }
            }
        };
    }
}
