package com.niblet.virtualization.jpa.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.niblet.virtualization.jpa.entity.MockApiRequestResponseEntity;

@Repository
public interface MockApiRequestResponseJpaRepository extends JpaRepository<MockApiRequestResponseEntity, Long> {

	public Optional<MockApiRequestResponseEntity> findById(Long id);

	public MockApiRequestResponseEntity findByApiPathAndRequestVerbAndRequestHeadersAndRequestQueryParameters(
			String apiPath, String requestVerb, String requestHeaders, String requestQueryParameters);

}
