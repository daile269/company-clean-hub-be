package com.company.company_clean_hub_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CompanyCleanHubBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(CompanyCleanHubBeApplication.class, args);
	}

}
