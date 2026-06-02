package com.junhyun.template.global.security;

import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

	public String createToken(String subject) {
		// TODO: Implement JWT creation with signing key, claims, and expiration.
		throw new UnsupportedOperationException("JWT token creation is not implemented yet.");
	}

	public boolean validateToken(String token) {
		// TODO: Implement JWT signature and expiration validation.
		return false;
	}

	public String getSubject(String token) {
		// TODO: Parse JWT claims and return the subject.
		return null;
	}
}
