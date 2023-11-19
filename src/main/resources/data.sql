DROP TABLE IF EXISTS MOCK_API_REQUEST_RESPONSE;

CREATE TABLE MOCK_API_REQUEST_RESPONSE (
	ID BIGINT NOT NULL,
	API_PATH VARCHAR(256) NOT NULL,
    REQUEST_VERB VARCHAR(10) NOT NULL,
    REQUEST_HEADERS VARCHAR(512) NULL,
    REQUEST_PATH_PARAMETERS VARCHAR(256) NOT NULL,
    REQUEST_QUERY_PARAMETERS VARCHAR(256) NOT NULL,
    RESPONSE_BODY TEXT NULL,
    RESPONSE_STATUS SMALLINT NOT NULL,
    PRIMARY KEY(ID)
);

INSERT INTO MOCK_API_REQUEST_RESPONSE
(ID, API_PATH, REQUEST_VERB, REQUEST_HEADERS, REQUEST_PATH_PARAMETERS, REQUEST_QUERY_PARAMETERS, RESPONSE_BODY, RESPONSE_STATUS)
VALUES
(1, '/consumer', 'GET', '.*', '.*', '.*', null, '200');
