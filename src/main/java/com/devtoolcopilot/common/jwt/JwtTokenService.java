package com.devtoolcopilot.common.jwt;

import java.util.Optional;

public interface JwtTokenService {
    String generateToken(Long userId);

    Optional<Long> parseUserId(String token);
}
