package com.gan.authservice.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing so {@code @CreatedDate}/{@code @LastModifiedDate} on
 * {@link com.gan.authservice.model.BaseEntity} are populated automatically (via the
 * {@code AuditingEntityListener}). No {@code AuditorAware} is registered — only timestamps are
 * audited, not the acting user.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfiguration {
}
