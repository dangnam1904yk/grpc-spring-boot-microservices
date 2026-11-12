package com.project.auth.grpc;

import com.project.common.security.AuthUser;
import com.project.common.security.CurrentUser;
import com.project.grpc.auth.v1.AuthorizationServiceGrpc;
import com.project.grpc.auth.v1.GetAuthoritiesRequest;
import com.project.grpc.auth.v1.GetAuthoritiesResponse;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * Các service khác (common-grpc: AuthServiceAuthorityResolver) hỏi role + quyền của user mang token gửi kèm.
 * Lúc xác thực RPC này, AuthorityResolver của auth-service (SecurityConfig) đã đọc role + quyền từ DB, nên chỉ cần
 * trả về {@link CurrentUser}.
 */
@GrpcService
public class AuthorizationGrpcService extends AuthorizationServiceGrpc.AuthorizationServiceImplBase {

    @Override
    public void getAuthorities(GetAuthoritiesRequest request, StreamObserver<GetAuthoritiesResponse> responseObserver) {
        AuthUser user = CurrentUser.require();
        responseObserver.onNext(GetAuthoritiesResponse.newBuilder()
                .addAllRoles(user.roles().stream().sorted().toList())
                .addAllPermissions(user.permissions().stream().sorted().toList())
                .build());
        responseObserver.onCompleted();
    }
}
