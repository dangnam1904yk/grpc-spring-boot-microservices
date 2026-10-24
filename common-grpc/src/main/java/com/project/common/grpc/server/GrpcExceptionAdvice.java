package com.project.common.grpc.server;

import org.springframework.dao.ConcurrencyFailureException;

import com.project.common.exception.AppException;

import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;

/**
 * Chuyển exception thành gRPC Status chuẩn để client phân biệt được loại lỗi (NOT_FOUND, ABORTED...).
 */
@Slf4j
@GrpcAdvice
public class GrpcExceptionAdvice {

    @GrpcExceptionHandler(AppException.class)
    public Status handleApp(AppException e) {
        return Status.fromCode(e.getErrorCode().getGrpcCode()).withDescription(e.getMessage());
    }

    @GrpcExceptionHandler(ConcurrencyFailureException.class)
    public Status handleConcurrencyFailure(ConcurrencyFailureException e) {
        return Status.ABORTED.withDescription("Dữ liệu đã bị thay đổi bởi request khác");
    }

    @GrpcExceptionHandler(Exception.class)
    public Status handleUnknown(Exception e) {
        log.error("Unhandled gRPC exception", e);
        return Status.INTERNAL.withDescription("Lỗi hệ thống");
    }
}
