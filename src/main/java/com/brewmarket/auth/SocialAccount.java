package com.brewmarket.auth;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "social_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_subject", nullable = false, updatable = false, length = 255)
    private String providerSubject;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public SocialAccount(Long userId, AuthProvider provider, String providerSubject) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("회원 ID는 양수여야 합니다.");
        }

        if (provider == null) {
            throw new IllegalArgumentException("로그인 제공자는 필수입니다.");
        }

        if (providerSubject == null || providerSubject.isBlank() || providerSubject.length() > 255) {
            throw new IllegalArgumentException("외부 계정 식별자는 공백이 아닌 255자 이하여야 합니다.");
        }

        this.userId = userId;
        this.provider = provider;
        this.providerSubject = providerSubject;
    }
}
