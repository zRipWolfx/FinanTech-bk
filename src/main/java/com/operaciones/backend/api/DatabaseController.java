package com.operaciones.backend.api;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/db")
public class DatabaseController {

    private final DataSource dataSource;

    public DatabaseController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/status")
    public Map<String, Object> status() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<String> tables = new ArrayList<>();

            try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, "%", new String[] {"TABLE"})) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString("TABLE_NAME"));
                }
            }

            payload.put("status", "ok");
            payload.put("database", connection.getCatalog());
            payload.put("product", metaData.getDatabaseProductName());
            payload.put("version", metaData.getDatabaseProductVersion());
            payload.put("tables", tables);
        }

        return payload;
    }
}
