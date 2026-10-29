package com.project.auth.service;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sửa schema cũ mà {@code ddl-auto: update} không tự sửa được.
 * <p>
 * Trước đây role là enum Java, Hibernate tạo cột {@code user_role.role} kiểu {@code enum('USER','ADMIN')} trên
 * MySQL. Role giờ là bảng (thêm được role mới), nên cột phải là varchar.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class LegacySchemaFixer implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        if (!isMySql()) {
            return;
        }
        String type = jdbcTemplate.query("""
                select data_type from information_schema.columns
                where table_schema = database() and table_name = 'user_role' and column_name = 'role'""",
                rs -> rs.next() ? rs.getString(1) : null);
        if ("enum".equalsIgnoreCase(type)) {
            jdbcTemplate.execute("alter table user_role modify column role varchar(20) not null");
            log.info("Đã đổi cột user_role.role từ enum sang varchar(20)");
        }
    }

    private boolean isMySql() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return "MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        }
    }
}
