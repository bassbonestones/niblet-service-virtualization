package com.niblet.virtualization.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class EmbeddedJdbcConfig {

	@Bean
	DataSource dataSource() {
		try {
			var dbBuilder = new EmbeddedDatabaseBuilder();
			return dbBuilder.setType(EmbeddedDatabaseType.H2)
					.addScripts("classpath:data.sql").build();
		} catch (Exception e) {
			log.error("Embedded DataSource bean cannot be created!", e);
			return null;
		}
	}
}
