package com.brewmarket.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SocialAccountService {
    // 연결 정보를 조회할 저장소의 참조 보관
    private final SocialAccountRepository socialAccountRepository;

    // 조회 작업이므로 읽기 전용 트랜잭션으로 실행
    @Transactional(readOnly = true)
    public Optional<Long> findUserId(
            AuthProvider provider,
            String providerSubject
    ) {
        // 조회에 필요한 제공자가 없으면 잘못된 호출로 처리
        if (provider == null) {
            throw new IllegalArgumentException("로그인 제공자는 필수입니다.");
        }

        // 저장할 때와 마찬가지로 조회할 식별자도 유효해야 함
        if (providerSubject == null || providerSubject.isBlank() || providerSubject.length() > 255) {
            throw new IllegalArgumentException(
                    "외부 계정 식별자는 공백이 아닌 255자 이하여야 합니다."
            );
        }

        // 제공자와 외부 계정 식별자로 연결 정보 조회
        // 연결 정보가 있으면 브루마켓 회원 번호만 꺼내 반환
        // 연결 정보가 없으면 빈 Optional을 그대로 반환
        return socialAccountRepository
                .findByProviderAndProviderSubject(provider, providerSubject)
                .map(SocialAccount::getUserId);
    }

}
