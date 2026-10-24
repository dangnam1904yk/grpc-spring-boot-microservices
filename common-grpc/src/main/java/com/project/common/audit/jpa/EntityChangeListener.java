package com.project.common.audit.jpa;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.hibernate.event.spi.PostCommitDeleteEventListener;
import org.hibernate.event.spi.PostCommitInsertEventListener;
import org.hibernate.event.spi.PostCommitUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.audit.AuditContext;
import com.project.common.audit.AuditEvent;
import com.project.common.audit.AuditSink;
import com.project.common.audit.AuditType;
import com.project.common.audit.Sanitizer;
import com.project.common.id.UuidV7;

import lombok.extern.slf4j.Slf4j;

/**
 * Ghi lại nội dung thay đổi của mỗi entity JPA: CREATE (giá trị mới), UPDATE (chỉ field đổi: cũ -> mới),
 * DELETE (giá trị trước khi xoá).
 * <p>
 * Dùng listener "post-commit" của Hibernate: chỉ ghi khi transaction commit thành công, transaction rollback
 * thì không có log thay đổi giả. Câu lệnh JPQL/SQL ghi hàng loạt ({@code @Modifying @Query}) không đi qua
 * entity nên không được ghi.
 */
@Slf4j
public class EntityChangeListener implements PostCommitInsertEventListener, PostCommitUpdateEventListener,
        PostCommitDeleteEventListener {

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";

    private final AuditSink sink;
    private final Sanitizer sanitizer;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public EntityChangeListener(AuditSink sink, Sanitizer sanitizer, ObjectMapper objectMapper, String serviceName) {
        this.sink = sink;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        publish(CREATE, event.getPersister(), event.getId(), null, event.getState());
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        publish(UPDATE, event.getPersister(), event.getId(), event.getOldState(), event.getState());
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        publish(DELETE, event.getPersister(), event.getId(), event.getDeletedState(), null);
    }

    @Override
    public void onPostInsertCommitFailed(PostInsertEvent event) {
    }

    @Override
    public void onPostUpdateCommitFailed(PostUpdateEvent event) {
    }

    @Override
    public void onPostDeleteCommitFailed(PostDeleteEvent event) {
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return true;
    }

    private void publish(String action, EntityPersister persister, Object id, Object[] oldState, Object[] newState) {
        try {
            List<Map<String, Object>> changes = diff(action, persister, oldState, newState);
            if (changes.isEmpty()) {
                return;
            }
            AuditContext.User user = AuditContext.currentUser();
            sink.publish(AuditEvent.builder()
                    .id(UuidV7.next())
                    .type(AuditType.ENTITY_CHANGE)
                    .requestId(AuditContext.requestId())
                    .service(serviceName)
                    .timestamp(Instant.now())
                    .clientIp(AuditContext.clientIp())
                    .userId(user == null ? null : user.id())
                    .userEmail(user == null ? null : user.email())
                    .entityName(simpleName(persister.getEntityName()))
                    .entityId(String.valueOf(id))
                    .action(action)
                    .changes(objectMapper.writeValueAsString(changes))
                    .build());
        } catch (Exception e) {
            // Đã commit rồi: lỗi ghi log không được ảnh hưởng tới kết quả trả về
            log.warn("Không tạo được audit log {} {}#{}: {}", action, persister.getEntityName(), id, e.toString());
        }
    }

    private List<Map<String, Object>> diff(String action, EntityPersister persister, Object[] oldState,
            Object[] newState) {
        String[] names = persister.getPropertyNames();
        Type[] types = persister.getPropertyTypes();
        List<Map<String, Object>> changes = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            // Collection (OneToMany...) là bảng khác, thay đổi của nó được ghi ở entity con
            if (types[i].isCollectionType()) {
                continue;
            }
            Object oldValue = oldState == null ? null : value(oldState[i], types[i], persister);
            Object newValue = newState == null ? null : value(newState[i], types[i], persister);
            if (Objects.deepEquals(oldValue, newValue)) {
                continue;
            }
            boolean masked = sanitizer.isSensitiveField(names[i]);
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("field", names[i]);
            change.put("old", masked && oldValue != null ? Sanitizer.MASK : oldValue);
            change.put("new", masked && newValue != null ? Sanitizer.MASK : newValue);
            changes.add(change);
        }
        // UPDATE mà không biết giá trị cũ (entity detached): không so sánh được, vẫn ghi giá trị mới ở trên
        return changes;
    }

    /** Quan hệ ManyToOne/OneToOne chỉ ghi id của entity kia, không serialize cả object. */
    private static Object value(Object value, Type type, EntityPersister persister) {
        if (value == null) {
            return null;
        }
        if (type.isEntityType()) {
            return String.valueOf(persister.getFactory().getPersistenceUnitUtil().getIdentifier(value));
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        return value.toString();
    }

    private static String simpleName(String entityName) {
        int dot = entityName.lastIndexOf('.');
        return dot < 0 ? entityName : entityName.substring(dot + 1);
    }
}
