package com.shopsphere.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "jwt.secret=test-only-jwt-secret-key-0123456789abcdef0123456789"
})
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
