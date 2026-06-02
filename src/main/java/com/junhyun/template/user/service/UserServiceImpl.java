package com.junhyun.template.user.service;

import com.junhyun.template.user.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

	@Override
	public UserResponse getSampleUser() {
		return UserResponse.sample();
	}
}
