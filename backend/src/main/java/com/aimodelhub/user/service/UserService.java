package com.aimodelhub.user.service;

import com.aimodelhub.common.exception.BusinessException;
import com.aimodelhub.common.exception.NotFoundException;
import com.aimodelhub.security.JwtUtil;
import com.aimodelhub.security.TokenBlacklistService;
import com.aimodelhub.user.entity.FavoriteModel;
import com.aimodelhub.user.entity.SysUser;
import com.aimodelhub.user.mapper.FavoriteModelMapper;
import com.aimodelhub.user.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

import java.util.List;

@Service
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final FavoriteModelMapper favoriteModelMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TokenBlacklistService tokenBlacklistService;

    public UserService(SysUserMapper sysUserMapper,
                       FavoriteModelMapper favoriteModelMapper,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       RedisTemplate<String, Object> redisTemplate,
                       TokenBlacklistService tokenBlacklistService) {
        this.sysUserMapper = sysUserMapper;
        this.favoriteModelMapper = favoriteModelMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Transactional
    /**
     * 注册用户：校验用户名是否重复，密码用 BCrypt 哈希后入库，默认普通用户角色。
     */
    public SysUser register(String username, String password, String nickname,
                            String securityQuestion, String securityAnswer) {
        if (sysUserMapper.countByUsername(username) > 0) {
            throw new BusinessException("用户名已存在");
        }

        // 长度校验必须与数据库列宽（username/nickname VARCHAR(50)）一致：
        // 否则超长输入会在 INSERT 阶段抛 DataIntegrityViolationException，
        // 被兜底异常处理器转成 500，把「用户输入过长」误报成服务端故障。
        if (username.length() > 50) {
            throw new BusinessException("用户名最多 50 个字符");
        }
        if (nickname != null && nickname.length() > 50) {
            throw new BusinessException("昵称最多 50 个字符");
        }
        // 与前端的 6 位下限保持一致；服务端必须独立校验，不能只信任前端。
        if (password == null || password.length() < 6) {
            throw new BusinessException("密码长度至少 6 位");
        }

        // 手动校验——为兼容旧接口，没有在 DTO 上使用 @NotBlank
        if (!StringUtils.hasText(securityQuestion) || !StringUtils.hasText(securityAnswer)) {
            throw new BusinessException("必须填写密保问题和答案");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname != null ? nickname : username);
        user.setRole("user");
        user.setAvatar("🧑‍🔬");
        user.setSecurityQuestion(securityQuestion);
        user.setSecurityAnswer(passwordEncoder.encode(securityAnswer));
        user.onCreate();

        sysUserMapper.insert(user);
        return user;
    }

    /**
     * 登录校验：核对密码哈希，通过后签发 JWT 并返回令牌字符串。
     */
    public String login(String username, String password) {
        SysUser user = sysUserMapper.findByUsername(username);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        return jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
    }

    /**
     * 按用户名取密保问题（用于找回密码）。
     * 用户不存在或未设置密保时抛出 BusinessException。
     */
    public String getSecurityQuestion(String username) {
        SysUser user = sysUserMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException("用户不存在或未设置密保");
        }

        if (!StringUtils.hasText(user.getSecurityQuestion())) {
            throw new BusinessException("该用户未设置密保问题，无法找回密码");
        }

        return user.getSecurityQuestion();
    }

    /**
     * 用密保答案重置密码。
     * 答案不匹配或未设置密保时抛出 BusinessException。
     */
    @Transactional
    public void resetPassword(String username, String securityAnswer, String newPassword) {
        // Redis 限流 key：最多允许 5 次错误，之后锁定 10 分钟
        String failKey = "pwd:reset:fail:" + username;

        // 读取失败次数（用 increment-by-0 绕过 JSON 值序列化器——
        // 它无法反序列化 Redis INCR 写入的裸整数）。
        // 只在 key 已存在时调用：对不存在的 key 做自增会创建一个值为 0、
        // 且没有 TTL 的 key，造成永久残留。
        int failCount = 0;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(failKey))) {
            Long count = redisTemplate.opsForValue().increment(failKey, 0);
            failCount = count == null ? 0 : count.intValue();
        }
        if (failCount >= 5) {
            throw new BusinessException("密保尝试次数过多，请10分钟后再试");
        }

        SysUser user = sysUserMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException("用户不存在或未设置密保");
        }

        if (!StringUtils.hasText(user.getSecurityQuestion())) {
            throw new BusinessException("该用户未设置密保问题，无法找回密码");
        }

        if (!StringUtils.hasText(user.getSecurityAnswer())) {
            throw new BusinessException("该用户未设置密保问题，无法找回密码");
        }

        // 校验用户提交的答案与库中 BCrypt 哈希是否匹配
        if (!passwordEncoder.matches(securityAnswer, user.getSecurityAnswer())) {
            // 每次答错都累加失败次数，并设置 10 分钟过期
            redisTemplate.opsForValue().increment(failKey, 1);
            redisTemplate.expire(failKey, 10, TimeUnit.MINUTES);
            throw new BusinessException("密保答案错误");
        }

        // 答案正确——清空失败计数
        redisTemplate.delete(failKey);

        if (!StringUtils.hasText(newPassword)) {
            throw new BusinessException("新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new BusinessException("新密码长度至少 6 位");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.onUpdate();
        sysUserMapper.update(user);

        // 让本次重置之前签发的所有令牌立即失效
        tokenBlacklistService.revokeAllForUser(user.getId(), System.currentTimeMillis());
    }

    public SysUser getUserById(Long id) {
        SysUser user = sysUserMapper.findById(id);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return user;
    }

    public SysUser getUserByUsername(String username) {
        SysUser user = sysUserMapper.findByUsername(username);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return user;
    }

    public long getUserCount() {
        return sysUserMapper.count();
    }

    // 收藏相关操作
    /**
     * 添加收藏。设计上是幂等的：Mapper 使用 INSERT IGNORE，
     * 且表上有 (user_id, model_id) 唯一键，因此并发重复点击不会抛唯一键冲突。
     */
    @Transactional
    public void addFavorite(Long userId, Long modelId) {
        favoriteModelMapper.insert(new FavoriteModel(userId, modelId));
    }

    /** 取消收藏（按用户与模型删除关联记录）。 */
    @Transactional
    public void removeFavorite(Long userId, Long modelId) {
        favoriteModelMapper.deleteByUserIdAndModelId(userId, modelId);
    }

    public List<FavoriteModel> getUserFavorites(Long userId) {
        return favoriteModelMapper.findByUserId(userId);
    }

    public boolean isFavorite(Long userId, Long modelId) {
        return favoriteModelMapper.countByUserIdAndModelId(userId, modelId) > 0;
    }
}
