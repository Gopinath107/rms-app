package com.ris.rms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.ris.rms.repository.UserAccountRepository;
import com.ris.rms.entity.UserAccount;

@SpringBootTest
class RmsAppApplicationTests {

	@Autowired
	private com.ris.rms.repository.DemandRepository demandRepo;

	@Test
	void contextLoads() {
		System.out.println("=== DEMANDS START ===");
		for (com.ris.rms.entity.Demand d : demandRepo.findAll()) {
			System.out.println("DEMAND: ID=" + d.getDemandid() + ", TITLE=" + d.getDemandtitle() + ", BUDGET=" + d.getBudget());
		}
		System.out.println("=== DEMANDS END ===");
	}

}
