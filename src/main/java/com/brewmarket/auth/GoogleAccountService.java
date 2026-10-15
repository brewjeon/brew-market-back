package com.brewmarket.auth;

import com.brewmarket.user.User;
import com.brewmarket.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Service
@RequiredArgsConstructor
public class GoogleAccountService {
    private final SocialAccountService socialAccountService;
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;

    // 회원 저장과 소셜 계정 연결 저장을 하나의 작업으로 처리
    @Transactional
    public Long getOrCreateUserId(
            String subject,
            String email,
            boolean emailVerified,
            String nickname
    ) {
        // 인증이 검증된 구글 계정 식별자로 기존 연결 조회
        // 식별자의 공백·길이 검증은 조회 서비스에서 수행
        Optional<Long> existingUserId = socialAccountService.findUserId(
                AuthProvider.GOOGLE,
                subject
        );

        // 기존 회원이면 가입 처리를 하지 않고 회원 번호 반환
        // 이메일과 닉네임도 여기서 덮어쓰지 않음
        if (existingUserId.isPresent()) {
            return existingUserId.get();
        }

        // 신규 가입에는 구글이 확인한 이메일만 사용
        if (!emailVerified) {
            throw new IllegalArgumentException(
                    "확인된 이메일이 필요합니다."
            );
        }

        // 저장할 이메일의 필수 여부와 길이 확인
        if (email == null || email.isBlank() || email.length() > 255) {
            throw new IllegalArgumentException(
                    "이메일은 공백이 아닌 255자 이하여야 합니다."
            );
        }

        // 닉네임은 회원 테이블의 길이 제한에 맞게 확인
        if (nickname == null
                || nickname.isBlank()
                || nickname.length() > 50) {
            throw new IllegalArgumentException(
                    "닉네임은 공백이 아닌 50자 이하여야 합니다."
            );
        }

        // 입력 형식이 아니라 기존 회원과 충돌하는 상태이므로 구분
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalStateException(
                    "기존 회원의 이메일입니다. 별도의 계정 연결 확인이 필요합니다."
            );
        }

        // 브루마켓 회원 저장 후 생성된 회원 번호 확보
        User user = userRepository.saveAndFlush(
                new User(email, nickname)
        );

        // 생성된 회원과 인증된 구글 계정의 연결 정보 저장
         SocialAccount account = new SocialAccount(
                 user.getId(),
                 AuthProvider.GOOGLE,
                 subject
         );

         // 여기서 저장이 실패하면 앞의 회원 저장도 함께 롤백
        socialAccountRepository.saveAndFlush(account);

        return user.getId();
    }
}
