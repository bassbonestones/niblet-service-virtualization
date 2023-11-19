package com.niblet.virtualization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@EntityScan(basePackages = "com.niblet.nibletservicevirtualization.jpa.entity")
@EnableJpaRepositories(basePackages = "com.niblet.nibletservicevirtualization.jpa.repository")
public class NibletServiceVirtualizationApplication {

	public static void main(String[] args) {
		SpringApplication.run(NibletServiceVirtualizationApplication.class, args);
	}

}
