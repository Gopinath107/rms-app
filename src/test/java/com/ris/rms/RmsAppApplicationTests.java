package com.ris.rms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.ris.rms.repository.UserAccountRepository;
import com.ris.rms.entity.UserAccount;

@SpringBootTest
class RmsAppApplicationTests {

	@Autowired
	private UserAccountRepository userAccountRepo;

	@Test
	void contextLoads() {
		System.out.println("=== USER ACCOUNTS START ===");
		for (UserAccount u : userAccountRepo.findAll()) {
			System.out.println("USER: " + u.getEmail() + " | PASS: " + u.getPasswordHash());
		}
		System.out.println("=== USER ACCOUNTS END ===");
	}

}
