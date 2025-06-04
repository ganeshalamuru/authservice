package com.gan.authservice.model.security;

import com.gan.authservice.model.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "app_user_credential")
public class UserCredential extends BaseEntity {

    @JoinColumn(name = "user_id", referencedColumnName = "id")
    @ManyToOne(cascade = CascadeType.ALL)
    private User user;
    @Column(name = "username", nullable = false)
    private String username;
    @Column(name = "plain_password", nullable = false)
    private String plainPassword;
    @Column(name = "encrypted_password", nullable = false)
    private String encryptedPassword;

}
