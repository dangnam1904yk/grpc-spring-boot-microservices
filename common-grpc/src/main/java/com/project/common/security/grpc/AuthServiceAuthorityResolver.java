package com.project.common.security.grpc;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.security.oauth2.jwt.Jwt;

import com.project.common.security.Authorities;
import com.project.common.security.AuthorityCache;
import com.project.common.security.AuthorityResolver;
import com.project.grpc.auth.v1.AuthorizationServiceGrpc;
import com.project.grpc.auth.v1.GetAuthoritiesRequest;
import com.project.grpc.auth.v1.GetAuthoritiesResponse;

import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Role + quyền của user: đọc {@link AuthorityCache} (Redis, theo {@code sub}), không có thì hỏi auth-service
 * (gRPC AuthorizationService.GetAuthorities) rồi ghi vào cache.
 * <p>
 * Gọi kèm chính token đang được xác thực (lúc này SecurityContext chưa có user nên
 * {@link BearerTokenPropagationInterceptor} không tự gắn được). auth-service lỗi / không kết nối được thì trả về
 * {@link Authorities#USER_ONLY} (không cache): user mất quyền trên dữ liệu người khác (403) nhưng request trên
 * dữ liệu của chính mình vẫn chạy.
 */
@Slf4j
public class AuthServiceAuthorityResolver implements AuthorityResolver {

    private final AuthorizationServiceGrpc.AuthorizationServiceBlockingStub stub;
    private final AuthorityCache cache;
    private final Duration deadline;

    public AuthServiceAuthorityResolver(AuthorizationServiceGrpc.AuthorizationServiceBlockingStub stub,
            AuthorityCache cache, Duration deadline) {
        this.stub = stub;
        this.cache = cache;
        this.deadline = deadline;
    }

    @Override
    public Authorities resolve(Jwt jwt) {
        String userId = jwt.getSubject();
        Optional<Authorities> cached = cache.get(userId);
        if (cached.isPresent()) {
            return cached.get();
        }
        try {
            Metadata headers = new Metadata();
            headers.put(BearerTokenPropagationInterceptor.AUTHORIZATION, "Bearer " + jwt.getTokenValue());
            GetAuthoritiesResponse response = stub
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                    .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                    .getAuthorities(GetAuthoritiesRequest.getDefaultInstance());
            Authorities authorities = new Authorities(Set.copyOf(response.getRolesList()),
                    Set.copyOf(response.getPermissionsList()));
            cache.put(userId, authorities);
            return authorities;
        } catch (StatusRuntimeException e) {
            log.warn("Không lấy được quyền của user {} từ auth-service ({}), chỉ tính role USER", userId,
                    e.getStatus().getCode());
            return Authorities.USER_ONLY;
        }
    }
}
