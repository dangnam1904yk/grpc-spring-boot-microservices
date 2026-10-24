package com.project.common.jpa;

import java.time.Instant;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Lớp cha cho entity: tự ghi ai tạo / sửa lần cuối và lúc nào (Spring Data JPA Auditing, bật sẵn trong common-grpc).
 * <pre>
 * &#64;Entity
 * public class Category extends BaseEntity {
 *     &#64;Id &#64;UUIDV7 UUID id;
 *     ...
 * }
 * </pre>
 * Người thao tác = claim {@code sub} của JWT (id user bên auth-service); không có user (job nền, khởi tạo dữ liệu)
 * thì là {@value JpaAuditingAutoConfiguration#SYSTEM_USER}. Không cần tự set các field này.
 * <p>
 * Chỉ áp dụng khi lưu qua entity; câu lệnh {@code @Modifying @Query} cập nhật hàng loạt không đi qua đây.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter(AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class BaseEntity {

    /** Id người tạo. */
    @CreatedBy
    @Column(name = "create_by", length = 64, updatable = false)
    String createBy;

    /** Id người sửa lần cuối. */
    @LastModifiedBy
    @Column(name = "last_modify", length = 64)
    String lastModify;

    @CreatedDate
    @Column(name = "create_date", nullable = false, updatable = false)
    Instant createDate;

    @LastModifiedDate
    @Column(name = "last_modify_date", nullable = false)
    Instant lastModifyDate;
}
