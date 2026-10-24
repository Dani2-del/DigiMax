package com.Servicio.Acceso.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "admin_audit_events")
public class AdminAuditEvent {

    @Id
    private String id;

    private String administratorId;
    private String administratorEmail;
    private String action;
    private String targetId;
    private String details;
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAdministratorId() { return administratorId; }
    public void setAdministratorId(String administratorId) { this.administratorId = administratorId; }
    public String getAdministratorEmail() { return administratorEmail; }
    public void setAdministratorEmail(String administratorEmail) { this.administratorEmail = administratorEmail; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
