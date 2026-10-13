package com.brewmarket.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations
    ) throws Exception {

        http
                .authorizeHttpRequests(authorize -> authorize
                        // 서버 내부의 오류 응답 처리가 보안 검사에 다시 막히지 않도록 허용
                        // 외부에서 /error로 직접 요청하는 것과는 다른 조건
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // 상태 확인은 로그인 없이 허용
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        // 기존 회원 등록 기능 유지
                        // 이메일 소유권 확인이나 구글 로그인 완료를 의미하지는 않음
                        .requestMatchers(HttpMethod.POST, "/users").permitAll()

                        // 게시글 등록은 인증된 사용자만 허용
                        // 컨트롤러 작성 전에 접근 규칙부터 정의
                        .requestMatchers(HttpMethod.POST, "/posts").authenticated()

                        // 명시하지 않은 요청은 기본 거부
                        // 새로운 기능을 열 때 접근 규칙도 함께 검토
                        .anyRequest().denyAll()
                )

                // 기본 로그인 화면과 아이디·비밀번호 인증은 이번에 사용하지 않음
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // 로그아웃은 로그인 방식을 연결할 때 별도로 구현
                .logout(AbstractHttpConfigurer::disable)

                .exceptionHandling(exception -> exception
                        // 인증이 필요한 요청에 로그인 정보가 없으면 401 반환
                        // 이번에는 상태 확인 코드만 반환하고 오류 본문 통일은 후속 작업
                        .authenticationEntryPoint((request, response, cause) ->
                                response.setStatus(
                                        HttpServletResponse.SC_UNAUTHORIZED
                                )
                        )

                        // 인증은 되었지만 접근이 허용되지 않으면 403 반환
                        .accessDeniedHandler((request, response, cause) ->
                                response.setStatus(
                                        HttpServletResponse.SC_FORBIDDEN
                                )
                        )
                );

        // 외부 로그인 설정이 등록되어 있는지 확인
        // 설정이 없는 기존 테스트에서는 null 반환
        ClientRegistrationRepository registrations = clientRegistrations.getIfAvailable();

        // 설정이 있는 환경에서만 외부 로그인 기능 활성화
        if (registrations != null) {
            http.oauth2Login(oauth2 -> oauth2
                    // 위에서 확인한 클라이언트 설정 저장소 사용
                    .clientRegistrationRepository(registrations)
                    
                    // 인증 성공 후에는 임시로 상태 확인 주소로 이동
                    // true: 이전에 접근했던 주소 대신 항상 이 주소 사용
                    .defaultSuccessUrl("/health", true)
                    
            );
        }
        
        // 위에서 정의한 규칙으로 보안 필터 묶음을 생성
        return http.build();
    }
}
