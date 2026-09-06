package com.brewmarket.user;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(
            name = "create_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime createAt;

    protected User() {

    }

    public User(String email, String nickname) {
        this.email = email;
        this.nickname = nickname;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }
}