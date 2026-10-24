package com.project.common.jpa;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hibernate.annotations.IdGeneratorType;

/**
 * Id tự sinh dạng UUID version 7 (tăng dần theo thời gian), dùng chung cho mọi service:
 * <pre>
 * &#64;Id
 * &#64;UUIDV7
 * UUID id;          // hoặc String id (lưu dạng chuỗi 36 ký tự)
 * </pre>
 * Thay cho {@code @GeneratedValue}. Nếu entity đã được gán id trước khi lưu thì giữ nguyên id đó.
 *
 * @see UuidV7Generator
 */
@IdGeneratorType(UuidV7Generator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
public @interface UUIDV7 {
}
