package com.gan.authservice.model.security;

import com.gan.authservice.model.security.enums.RoleName;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private RoleName name;

    protected Role() {

    }

    public Role (RoleName name) {
        this.name = name;
    }

    public String getName() {
        return name.name();
    }

}
