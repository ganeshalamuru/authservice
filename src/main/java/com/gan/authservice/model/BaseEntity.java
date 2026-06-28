package com.gan.authservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID id;

    // Audit timestamps are populated by Spring Data JPA auditing (AuditingEntityListener) and stored
    // as timestamptz (UTC) — Instant carries no zone, so the instant is unambiguous across regions.
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Stored as VARCHAR + CHECK (see V6 migration); @Enumerated(STRING) maps the enum to the column.
    // Initialized here (rather than in a @PrePersist callback) so new rows default to ACTIVE.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.ACTIVE;

    /**
     * Marks the row soft-deleted: stamps {@code deletedAt} and flips {@code status} to INACTIVE. The
     * {@code deleted_at is null} {@code @SQLRestriction} on the soft-deletable entities then hides it
     * from every query (listings and the login/credential lookup), so it survives only as audit history.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
        this.status = Status.INACTIVE;
    }

}
