package com.project.common.security;

import java.util.List;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;

/** Role / quyền lấy từ claim "roles" / "permissions" của chính token (token test của TestJwt). */
public class ClaimAuthorityResolver implements AuthorityResolver {

    @Override
    public Authorities resolve(Jwt jwt) {
        return new Authorities(claim(jwt, AuthUser.ROLES_CLAIM), claim(jwt, AuthUser.PERMISSIONS_CLAIM));
    }

    private static Set<String> claim(Jwt jwt, String name) {
        List<String> values = jwt.getClaimAsStringList(name);
        return values == null ? Set.of() : Set.copyOf(values);
    }
}
