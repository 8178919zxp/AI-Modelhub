package com.aimodelhub.health;

import com.aimodelhub.common.api.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DataSource dataSource;

    @Value("${server.port:8080}")
    private int port;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 服务健康检查：返回应用状态、端口与数据库连通性。 */
    @GetMapping
    public Result<Map<String, Object>> health() {
        boolean dbOk = false;
        try (Connection conn = dataSource.getConnection()) {
            dbOk = conn.isValid(3);
        } catch (Exception ignored) {
        }

        return Result.ok(Map.of(
                "service", "backend",
                "status", dbOk ? "UP" : "DEGRADED",
                "port", port,
                "database", dbOk ? "connected" : "disconnected"
        ));
    }
}
