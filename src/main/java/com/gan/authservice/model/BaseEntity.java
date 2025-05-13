package com.gan.authservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;

@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;
    @Column(name = "created_at", nullable = false)
    private Long createdAt;
    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
    @Column(name = "active", nullable = false)
    private boolean active;
    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    public Long getCreatedAt() {
        return createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Long getId() {
        return id;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setMetaData() {
        long curTime = Instant.now().toEpochMilli();
        this.createdAt = curTime;
        this.updatedAt = curTime;
        this.active = false;
        this.deleted = false;
    }

}
