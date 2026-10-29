package com.project.auth.entity;

import java.util.UUID;

import com.project.common.jpa.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Menu / màn hình của frontend, dạng cây (parentId), đồng thời là đối tượng được phân quyền:
 * quyền = menu + thao tác, vd. "category:ADD". User thấy menu theo role ({@link Role#getMenuIds()}).
 * <p>
 * Tên cột giữ như bản trước (path, sort_order, active) để bảng đã có dữ liệu dùng tiếp được.
 */
@Entity
@Table(name = "authz_menu",
        uniqueConstraints = @UniqueConstraint(name = "uk_menu_code", columnNames = "code"),
        indexes = @Index(name = "idx_menu_parent_id", columnList = "parent_id"))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Menu extends BaseEntity {

    /** Không đổi sau khi tạo: là một phần của mã quyền đã cấp cho role. */
    @Column(nullable = false, length = 50, updatable = false)
    String code;

    @Column(nullable = false, length = 255)
    String name;

    /** Route ở frontend; menu nhóm (chỉ chứa menu con) thì để trống. */
    @Column(name = "path", length = 500)
    String url;

    /** Tên icon của Ant Design, vd. "UserOutlined". */
    @Column(length = 100)
    String icon;

    @Column(name = "parent_id")
    UUID parentId;

    @Column(name = "sort_order", nullable = false)
    int orderIndex;

    /** Menu tắt thì ẩn khỏi sidebar (cùng menu con), nhưng quyền đã cấp trên menu vẫn còn hiệu lực. */
    @Column(nullable = false)
    boolean active = true;

    @Version
    Long version;
}
