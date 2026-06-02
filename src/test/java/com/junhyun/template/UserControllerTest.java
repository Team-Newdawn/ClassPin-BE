package com.junhyun.template;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.junhyun.template.user.controller.UserController;
import com.junhyun.template.user.dto.response.UserResponse;
import com.junhyun.template.user.service.UserService;
import com.junhyun.template.global.security.JwtTokenProvider;
import com.junhyun.template.global.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@Test
	void getUsersReturnsSampleUserResponse() throws Exception {
		when(userService.getSampleUser()).thenReturn(new UserResponse(1L, "user@example.com", "sample-user"));

		mockMvc.perform(get("/api/users"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.message").value("Request succeeded"))
			.andExpect(jsonPath("$.data.id").value(1L))
			.andExpect(jsonPath("$.data.email").value("user@example.com"))
			.andExpect(jsonPath("$.data.nickname").value("sample-user"));
	}
}
