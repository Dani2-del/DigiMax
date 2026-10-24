package com.Servicio.Acceso.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

import com.Servicio.Acceso.model.AdminAuditEvent;

public interface AdminAuditEventRepository extends MongoRepository<AdminAuditEvent, String> {

    List<AdminAuditEvent> findTop8ByOrderByCreatedAtDesc();
}
