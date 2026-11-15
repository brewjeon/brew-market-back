package com.brewmarket.neighborhood;

import com.brewmarket.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NeighborhoodService {
    private static final Duration VALIDITY = Duration.ofDays(30);
    private static final double MAX_ACCURACY_METERS = 100;

    private final NeighborhoodRepository neighborhoods;
    private final NeighborhoodVerificationRepository verifications;
    private final UserRepository users;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<NeighborhoodOption> active(Long userId) {
        return verifications.findActive(userId, Instant.now());
    }

    public String findName(Long neighborhoodId) {
        if (neighborhoodId == null) return null;
        return neighborhoods.findById(neighborhoodId).map(neighborhood -> neighborhood.name).orElse(null);
    }

    @Transactional
    public NeighborhoodOption verify(Long userId, double latitude, double longitude, double accuracy) {
        if (!Double.isFinite(latitude) || Math.abs(latitude) > 90
                || !Double.isFinite(longitude) || Math.abs(longitude) > 180
                || !Double.isFinite(accuracy) || accuracy < 0 || accuracy > MAX_ACCURACY_METERS) {
            throw new NeighborhoodException(HttpStatus.BAD_REQUEST, "위치를 정확하게 확인할 수 없습니다. 다시 시도해 주세요.");
        }
        var matches = neighborhoods.findContaining(longitude, latitude);
        if (matches.size() != 1) {
            throw new NeighborhoodException(HttpStatus.BAD_REQUEST, "현재 위치에서 인증할 수 있는 동네를 찾지 못했습니다.");
        }
        var neighborhood = neighborhoods.findById(matches.getFirst()).orElseThrow();
        users.findByIdForUpdate(userId)
                .orElseThrow(() -> new NeighborhoodException(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."));

        Instant now = Instant.now();
        var key = new NeighborhoodVerification.Key(userId, neighborhood.id);
        var existing = verifications.findById(key).orElse(null);
        if ((existing == null || !existing.expiresAt.isAfter(now))
                && verifications.countByIdUserIdAndExpiresAtAfter(userId, now) >= 2) {
            throw new NeighborhoodException(HttpStatus.CONFLICT, "동네는 최대 두 곳까지 인증할 수 있습니다.");
        }
        Instant expiresAt = now.plus(VALIDITY);
        if (existing == null) {
            entityManager.persist(new NeighborhoodVerification(key, expiresAt));
        } else {
            existing.expiresAt = expiresAt;
        }
        return new NeighborhoodOption(neighborhood.id, neighborhood.name, expiresAt);
    }

    public void requireActive(Long userId, Long neighborhoodId) {
        if (neighborhoodId == null || verifications.findById(new NeighborhoodVerification.Key(userId, neighborhoodId))
                .filter(verification -> verification.expiresAt.isAfter(Instant.now())).isEmpty()) {
            throw new NeighborhoodException(HttpStatus.FORBIDDEN, "게시하기 전에 거래 동네를 인증해 주세요.");
        }
    }
}
