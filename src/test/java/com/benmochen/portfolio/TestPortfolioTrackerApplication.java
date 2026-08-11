package com.benmochen.portfolio;

import org.springframework.boot.SpringApplication;

public class TestPortfolioTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.from(PortfolioTrackerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
