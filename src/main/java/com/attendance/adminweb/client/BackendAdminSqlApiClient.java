package com.attendance.adminweb.client;

import com.attendance.adminweb.config.BackendAdminApiProperties;
import com.attendance.adminweb.model.SqlQueryResult;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Component
public class BackendAdminSqlApiClient {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String ADMIN_CODE_HEADER = "X-Admin-Employee-Code";

    private final RestClient restClient;
    private final BackendAdminApiProperties properties;

    public BackendAdminSqlApiClient(BackendAdminApiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .build();
    }

    public SqlQueryResult executeReadOnlySql(String adminEmployeeCode, String queryText) {
        try {
            SqlQueryResultResponse response = restClient.post()
                .uri("/api/internal/admin/sql/query")
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SqlQueryRequest(queryText))
                .retrieve()
                .body(SqlQueryResultResponse.class);

            if (response == null) {
                throw new IllegalStateException("SQL 조회 응답이 비어 있습니다.");
            }

            return new SqlQueryResult(response.columns(), response.rows(), response.rowLimit(), response.truncated());
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    public byte[] exportSqlQueryExcel(String adminEmployeeCode, String queryText) {
        try {
            byte[] response = restClient.post()
                .uri("/api/internal/admin/sql/excel")
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SqlQueryRequest(queryText))
                .retrieve()
                .body(byte[].class);

            if (response == null) {
                throw new IllegalStateException("SQL 엑셀 응답이 비어 있습니다.");
            }

            return response;
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    private String extractMessage(HttpStatusCodeException exception) {
        String body = exception.getResponseBodyAsString();
        if (StringUtils.hasText(body) && body.contains("\"message\"")) {
            int keyIndex = body.indexOf("\"message\"");
            int colonIndex = body.indexOf(':', keyIndex);
            int startQuote = body.indexOf('"', colonIndex + 1);
            int endQuote = body.indexOf('"', startQuote + 1);
            if (startQuote >= 0 && endQuote > startQuote) {
                return body.substring(startQuote + 1, endQuote);
            }
        }
        return "백엔드 SQL API 호출에 실패했습니다.";
    }

    private record SqlQueryRequest(String queryText) {
    }

    private record SqlQueryResultResponse(
        List<String> columns,
        List<List<String>> rows,
        int rowLimit,
        boolean truncated
    ) {
    }
}
