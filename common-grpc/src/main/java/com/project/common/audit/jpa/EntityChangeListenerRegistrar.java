package com.project.common.audit.jpa;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.beans.factory.InitializingBean;

import jakarta.persistence.EntityManagerFactory;

/** Gắn {@link EntityChangeListener} vào SessionFactory của Hibernate (không cần sửa entity nào). */
public class EntityChangeListenerRegistrar implements InitializingBean {

    private final EntityManagerFactory entityManagerFactory;
    private final EntityChangeListener listener;

    public EntityChangeListenerRegistrar(EntityManagerFactory entityManagerFactory, EntityChangeListener listener) {
        this.entityManagerFactory = entityManagerFactory;
        this.listener = listener;
    }

    @Override
    public void afterPropertiesSet() {
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry()
                .getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_COMMIT_INSERT, listener);
        registry.appendListeners(EventType.POST_COMMIT_UPDATE, listener);
        registry.appendListeners(EventType.POST_COMMIT_DELETE, listener);
    }
}
