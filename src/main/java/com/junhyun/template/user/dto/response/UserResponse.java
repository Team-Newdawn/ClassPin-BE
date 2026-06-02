package com.junhyun.template.user.dto.response;

import com.junhyun.template.user.domain.entity.User;

public record UserResponse(
	Long id,
	String email,
	String nickname
) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getNickname());
	}

	public static UserResponse sample() {
		return new UserResponse(1L, "user@example.com", "sample-user");
	}
}
