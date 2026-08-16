package com.benmochen.portfolio.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    /**
     * A BCrypt hash, never the password itself.
     *
     * BCrypt is deliberately slow and salts each hash individually, so two
     * users with the same password get different hashes and an attacker who
     * steals this table cannot precompute answers. A plain SHA-256 would be
     * fast enough to brute-force.
     */
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Never include the hash. An accidental log line or error response
     * containing this object should not leak it.
     */
    @Override
    public String toString() {
        return "AppUser{id=" + id + ", username='" + username + "'}";
    }
}
