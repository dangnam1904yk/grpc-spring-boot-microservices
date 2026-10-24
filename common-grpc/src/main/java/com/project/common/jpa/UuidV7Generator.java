package com.project.common.jpa;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.UUID;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.AnnotationBasedGenerator;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.persister.entity.EntityPersister;

import com.project.common.id.UuidV7;

/**
 * Generator của Hibernate cho {@link UUIDV7}: field kiểu {@link UUID} hoặc {@link String}.
 * <p>
 * Phải có constructor không tham số: trong Spring Boot, Hibernate tạo generator qua bean container của Spring,
 * constructor có tham số sẽ bị Spring hiểu là cần autowire và báo lỗi.
 */
public class UuidV7Generator implements BeforeExecutionGenerator, AnnotationBasedGenerator<UUIDV7> {

    /** Chỉ kiểm tra kiểu field lúc khởi động; không lưu trạng thái vì instance có thể dùng chung nhiều entity. */
    @Override
    public void initialize(UUIDV7 annotation, Member member, GeneratorCreationContext context) {
        Class<?> type = member instanceof Field field ? field.getType()
                : member instanceof Method method ? method.getReturnType()
                : UUID.class;
        if (!UUID.class.equals(type) && !String.class.equals(type)) {
            throw new IllegalArgumentException("@UUIDV7 chỉ dùng cho field kiểu UUID hoặc String: " + member);
        }
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue,
            EventType eventType) {
        EntityPersister persister = session.getEntityPersister(null, owner);
        // Entity đã được gán id thì giữ nguyên. Hibernate 6.6 luôn truyền currentValue = null khi persist,
        // nên phải tự đọc id từ entity
        Object assigned = persister.getIdentifier(owner, session);
        if (assigned != null) {
            return assigned;
        }
        UUID id = UuidV7.next();
        return String.class.equals(persister.getIdentifierType().getReturnedClass()) ? id.toString() : id;
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EnumSet.of(EventType.INSERT);
    }

    /** Entity đã có id (ví dụ id do service khác sinh sẵn) thì giữ nguyên, không sinh mới. */
    @Override
    public boolean allowAssignedIdentifiers() {
        return true;
    }
}
