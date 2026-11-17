package com.aimodelhub.support;

import com.aimodelhub.user.entity.SysUser;
import com.aimodelhub.user.mapper.SysUserMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 集成测试的用户夹具。
 * <p>
 * <b>为什么需要它：</b>原先的集成测试硬编码依赖 {@code 002_init_seed.sql} 里的
 * {@code admin / 123456} 与 {@code demo / 123456}，但开发库中的密码早已被改成 BCrypt
 * 哈希、{@code demo} 账号也被删除，于是测试稳定失败（登录返回 400）。
 * 测试不应依赖「某台机器上恰好存在的种子数据」，而应自己创建所需账号并在结束时清理。
 * <p>
 * 这里直接走 {@link SysUserMapper} 写入，不经过 HTTP：用户夹具与「正在被测的登录接口」
 * 解耦，避免测试因为注册接口自身的问题而无法准备环境。
 */
public final class IntegrationTestUsers {

    /** 夹具账号统一使用的明文密码。 */
    public static final String PASSWORD = "QaTest#2026";

    private IntegrationTestUsers() {
    }

    /**
     * 创建一个指定角色的测试用户。
     *
     * @param prefix 用户名前缀，实际用户名会附加时间戳与随机后缀，避免并发冲突
     * @param role   {@code admin} 或 {@code user}
     * @return 已落库的用户（含自增 id）
     */
    public static SysUser create(SysUserMapper mapper,
                                 PasswordEncoder encoder,
                                 String prefix,
                                 String role) {
        String suffix = System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
        String username = prefix + "_" + suffix;

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(encoder.encode(PASSWORD));
        user.setNickname("IT_" + prefix);
        user.setAvatar("🧪");
        user.setRole(role);
        user.setSecurityQuestion("integration-test");
        user.setSecurityAnswer(encoder.encode("integration-test"));
        user.onCreate();

        mapper.insert(user);
        return user;
    }

    /**
     * 删除夹具用户。使用 {@link JdbcTemplate} 而非 Mapper，
     * 是为了不给生产 Mapper 增加「仅供测试使用」的删除方法。
     */
    public static void delete(JdbcTemplate jdbc, Long userId) {
        if (userId != null) {
            jdbc.update("DELETE FROM sys_user WHERE id = ?", userId);
        }
    }
}
