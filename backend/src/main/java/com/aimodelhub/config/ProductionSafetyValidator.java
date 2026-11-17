package com.aimodelhub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 启动阶段对敏感配置做安全校验。
 * 策略
 *   环境（profile）检测到弱密钥 / 示例密钥时的行为
 *   {@code prod}拒绝启动（快速失败）
 *   {@code dev}、{@code docker}、默认打印告警，继续运行
 * 原因：仓库里带了示例 JWT 密钥和演示口令，方便本地启动。
 * 这些值一旦流入生产，任何能读到仓库（或其历史）的人都可以伪造管理员令牌，
 * 或者直接登录 MySQL / MinIO。因此生产环境必须「大声报错」，
 * 而不是带着已知密钥静默运行。
 * @see com.aimodelhub.security.JwtUtil
 */
@Component
public class ProductionSafetyValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyValidator.class);

    /**
     * 配置模板中的占位标记。
     * <p>
     * 这里刻意不再比对「某个具体的示例密钥字符串」：把可用的密钥字面量写进源码，
     * 本身就会让它在仓库公开后可被任何人拿去伪造令牌。改为识别占位标记，
     * 语义相同（说明用户还没真正配置），但不携带任何可用密钥。
     */
    private static final String PLACEHOLDER_MARK = "change_me";

    /** JWT 密钥的编码后最小长度（Base64 编码 32 字节为 44 字符，这里取宽松下界）。 */
    private static final int MIN_JWT_SECRET_LENGTH = 32;

    /**
     * 绝不能用于生产环境的弱口令。
     * 注意：{@code Set.of} 在类初始化时就会拒绝重复元素，
     * 这里必须保证字面量互不重复（否则会抛 ExceptionInInitializerError 导致启动失败）。
     */
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "123456", "12345678",
            "password", "passwd", "changeme", "admin", "root"
    );

    private final Environment environment;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${minio.secret-key:}")
    private String minioSecret;

    public ProductionSafetyValidator(Environment environment) {
        this.environment = environment;
    }

    /**
     * Bean 初始化完成后执行：按当前 profile 检查 JWT、数据库、MinIO 的密钥强度。
     * 生产环境检测到示例密钥或弱口令时直接抛异常，阻止应用启动。
     */
    @Override
    public void afterPropertiesSet() {
        List<String> problems = new ArrayList<>();

        if (jwtSecret == null || jwtSecret.isBlank()) {
            problems.add("app.jwt.secret 为空");
        } else if (isPlaceholder(jwtSecret)) {
            problems.add("app.jwt.secret 仍是模板占位值");
        } else if (jwtSecret.trim().length() < MIN_JWT_SECRET_LENGTH) {
            problems.add("app.jwt.secret 过短（Base64 编码后应不少于 32 字符）");
        }

        if (isWeak(datasourcePassword)) {
            problems.add("spring.datasource.password 为空或弱口令");
        }
        if (isWeak(minioSecret)) {
            problems.add("minio.secret-key 为空或弱口令（若禁用文件存储可忽略）");
        }

        if (problems.isEmpty()) {
            log.info("密钥检查通过（profile={}）", String.join(",", environment.getActiveProfiles()));
            return;
        }

        boolean producing = List.of(environment.getActiveProfiles()).contains("prod");
        String detail = String.join("；", problems);

        if (producing) {
            throw new IllegalStateException(
                    "生产环境检测到不安全的密钥配置，拒绝启动：" + detail
                            + "。请通过环境变量 JWT_SECRET / MYSQL_ROOT_PASSWORD / MINIO_ROOT_PASSWORD 注入，"
                            + "生成方式见 deploy/.env.example。");
        }

        log.warn("检测到不安全的密钥配置（当前 profile 非 prod，仅告警）：{}", detail);
    }

    private static boolean isWeak(String value) {
        return value == null
                || value.isBlank()
                || isPlaceholder(value)
                || WEAK_PASSWORDS.contains(value.trim().toLowerCase());
    }

    /**
     * 判断某个配置值是否仍是模板占位（{@code CHANGE_ME} 或 {@code <...>} 形式）。
     * <p>
     * 抽成独立方法是因为三处检查都要用：JWT 密钥、数据库口令、MinIO secret。
     */
    private static boolean isPlaceholder(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase();
        return v.contains(PLACEHOLDER_MARK) || (v.startsWith("<") && v.endsWith(">"));
    }
}
