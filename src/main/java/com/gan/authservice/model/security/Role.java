package com.gan.authservice.model.security;

import com.gan.authservice.model.BaseEntity;
import com.gan.authservice.model.security.enums.RoleName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "app_role")
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "name")
    private RoleName name;

}
