package com.niblet.virtualization.jpa.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Repository;

import com.niblet.virtualization.jpa.entity.MockApiRequestResponseEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@Repository
public class MockApiRequestResponseManagedRepository {

	@PersistenceContext()
	EntityManager manager;

	private static final String QUERY_BY_REGEX = "SELECT ID, API_PATH, REQUEST_VERB, REQUEST_HEADERS, "
			+ "REQUEST_QUERY_PARAMETERS, RESPONSE_BODY, RESPONSE_STATUS "
			+ "FROM MOCK_API_REQUEST_RESPONSE WHERE REQUEST_VERB = '{httpMethod}' AND '{apiPath}' regexp API_PATH AND "
			+ "'{requestHeaders}' regexp REQUEST_HEADERS AND '{queryParameters}' regexp REQUEST_QUERY_PARAMETERS;";

	@SuppressWarnings("unchecked")
	public List<MockApiRequestResponseEntity> gatherApiMocksByRegexp(String httpMethod, String requestURI,
			String headers, String queryParameters) {

		String queryString = QUERY_BY_REGEX.replace("{httpMethod}", httpMethod).replace("{apiPath}", requestURI)
				.replace("{requestHeaders}", headers).replace("{queryParameters}", queryParameters);

		Query query = manager.createNativeQuery(queryString);
		List<Object[]> objList = query.getResultList();

		return mapObjectsToMockEntityList(objList);
	}

	private List<MockApiRequestResponseEntity> mapObjectsToMockEntityList(List<Object[]> objList) {

		List<MockApiRequestResponseEntity> apiMocksByRegexp = new ArrayList<>();

		for (Object[] obj : objList) {

			apiMocksByRegexp.add(mapSingleMockApiData(obj));
		}

		return apiMocksByRegexp;
	}

	private MockApiRequestResponseEntity mapSingleMockApiData(Object[] obj) {

		MockApiRequestResponseEntity mockApiData = new MockApiRequestResponseEntity();
		mockApiData.setId(Long.parseLong(String.valueOf(obj[0])));
		mockApiData.setApiPath(Objects.toString(obj[1]));
		mockApiData.setRequestVerb(Objects.toString(obj[2]));
		mockApiData.setRequestHeaders(Objects.toString(obj[3]));
		mockApiData.setRequestQueryParameters(Objects.toString(obj[4]));
		mockApiData.setResponseBody(Objects.toString(obj[5]));
		mockApiData.setResponseStatus(Objects.toString(obj[6]));

		return mockApiData;
	}

}
