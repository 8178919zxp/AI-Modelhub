package com.aimodelhub.config;

import com.aimodelhub.model.entity.ModelInfo;
import com.aimodelhub.model.mapper.ModelInfoMapper;
import com.aimodelhub.predict.mapper.PredictRecordMapper;
import com.aimodelhub.user.entity.SysUser;
import com.aimodelhub.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SysUserMapper sysUserMapper;
    private final ModelInfoMapper modelInfoMapper;
    private final PredictRecordMapper predictRecordMapper;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    /**
     * 仅在用户表为空时创建 admin / demo 演示账号所用口令。
     * <p>
     * 不再写死在代码里：可通过 {@code app.bootstrap.default-user-password}
     * 或环境变量 {@code APP_BOOTSTRAP_DEFAULT_USER_PASSWORD} 覆盖。
     * 默认值只是为了让「全新空库」能直接登录演示，生产环境请务必覆盖，
     * 否则 {@link ProductionSafetyValidator} 会拒绝启动。
     */
    @Value("${app.bootstrap.default-user-password:123456}")
    private String defaultUserPassword;

    public DataInitializer(SysUserMapper sysUserMapper,
                           ModelInfoMapper modelInfoMapper,
                           PredictRecordMapper predictRecordMapper,
                           PasswordEncoder passwordEncoder,
                           TransactionTemplate transactionTemplate) {
        this.sysUserMapper = sysUserMapper;
        this.modelInfoMapper = modelInfoMapper;
        this.predictRecordMapper = predictRecordMapper;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 应用启动后执行：修复历史脏数据、初始化默认用户与模型。
     * 所有操作都是幂等的，可安全重复执行。
     */
    @Override
    public void run(String... args) {
        fixHistoricalData();
        initUsers();
        initModels();
        repairModelApis();
    }

    /**
     * 修复历史脏数据：create_time / update_time 为 NULL 的记录。
     * 这里用 TransactionTemplate，是因为同类内部调用不会触发 @Transactional。
     * 采用批量 UPDATE 更可靠——JPA 的 @Column(updatable=false) 曾导致
     * create_time 无法通过 save() 回填。
     */
    public void fixHistoricalData() {
        transactionTemplate.execute(status -> {
            // 修复 sys_user 中为空的时间字段
            int userCreateFixed = sysUserMapper.fixNullCreateTimes();
            int userUpdateFixed = sysUserMapper.fixNullUpdateTimes();
            if (userCreateFixed > 0 || userUpdateFixed > 0) {
                log.info("Fixed NULL timestamps on sys_user rows (create_time={}, update_time={})",
                        userCreateFixed, userUpdateFixed);
            }

            // 通过批量更新修复 predict_record 中为空的 create_time
            try {
                int recordFixed = predictRecordMapper.fixNullCreateTimes();
                if (recordFixed > 0) {
                    log.info("Fixed NULL create_time on {} predict_record rows", recordFixed);
                }
            } catch (Exception e) {
                log.warn("Could not fix predict_record NULL timestamps: {}", e.getMessage());
                log.warn("Run sql/003_fix_null_create_time.sql manually if needed.");
            }
            return null;
        });
    }

    private void initUsers() {
        // 把历史上以明文存储的密码（旧种子 SQL 写入）重新哈希
        boolean fixed = false;
        for (SysUser user : sysUserMapper.findAll()) {
            String pw = user.getPassword();
            // BCrypt 哈希以 $2a$、$2b$ 或 $2y$ 开头
            if (pw != null && !pw.startsWith("$2")) {
                user.setPassword(passwordEncoder.encode(pw));
                user.onUpdate();
                sysUserMapper.update(user);
                fixed = true;
                log.info("已为用户 {} 更新密码为BCrypt编码", user.getUsername());
            }
        }
        if (fixed) {
            log.info("已有明文密码已全部迁移为BCrypt编码");
        }

        // 表为空时创建默认用户
        if (sysUserMapper.count() == 0) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(defaultUserPassword));
            admin.setNickname("超级管理员");
            admin.setAvatar("👨‍💼");
            admin.setRole("admin");
            admin.onCreate();
            sysUserMapper.insert(admin);

            SysUser demo = new SysUser();
            demo.setUsername("demo");
            demo.setPassword(passwordEncoder.encode(defaultUserPassword));
            demo.setNickname("演示用户");
            demo.setAvatar("🧑‍🔬");
            demo.setRole("user");
            demo.onCreate();
            sysUserMapper.insert(demo);

            // 不把口令写进日志：只提示账号与配置项位置
            log.info("初始用户创建完成: admin / demo（口令取自 app.bootstrap.default-user-password，请登录后立即修改）");
        }
    }

    /**
     * 自动修复 {@code model_info.model_api} 字段。
     * <p>
     * 早期版本的初始化器会把后端 URL（如 {@code /api/predict/image}）写进这一列，
     * 但该列在 {@code PredictController} 中被当作 model-service 的
     * {@code model_key} 使用。这类记录永远无法预测（FastAPI 会拒绝未知的 key），
     * 因此这里按模型名改写为正确的 key。无法识别的模型只打告警，不做猜测。
     */
    private void repairModelApis() {
        Map<String, String> keyByName = Map.of(
                "ResNet-50", "resnet50",
                "YOLOv8", "yolov8",
                "BERT-Base", "bert",
                "XGBoost", "xgboost",
                "U2Net", "u2net",
                "ChestCT", "chest_ct",
                "FlowerCNN", "flower",
                "WeatherGBM", "weather",
                "StockRegression", "stock",
                "ChineseSentiment", "chinese_sentiment"
        );

        for (ModelInfo model : modelInfoMapper.findAll()) {
            String api = model.getModelApi();
            if (api == null || !api.startsWith("/")) {
                continue;
            }
            String fixed = keyByName.get(model.getModelName());
            if (fixed == null) {
                log.warn("模型 id={} name={} 的 model_api='{}' 不是合法 model_key，请手动修正",
                        model.getId(), model.getModelName(), api);
                continue;
            }
            model.setModelApi(fixed);
            model.onUpdate();
            modelInfoMapper.update(model);
            log.info("已修正模型 id={} 的 model_api: '{}' → '{}'", model.getId(), api, fixed);
        }
    }

    private void initModels() {
        if (modelInfoMapper.count() > 0) {
            log.info("模型数据已存在，跳过初始化");
            return;
        }

        ModelInfo m1 = new ModelInfo();
        m1.setModelName("ResNet-50");
        m1.setModelType("image");
        m1.setAlgorithm("Deep Residual Learning");
        m1.setAccuracy(92.1);
        m1.setDescription("经典图像分类模型，基于深度残差网络，支持1000类图像识别");
        m1.setModelApi("resnet50");
        m1.setStatus(1);
        m1.onCreate();
        modelInfoMapper.insert(m1);

        ModelInfo m2 = new ModelInfo();
        m2.setModelName("YOLOv8");
        m2.setModelType("object");
        m2.setAlgorithm("You Only Look Once v8");
        m2.setAccuracy(95.3);
        m2.setDescription("经典目标检测模型，支持实时多目标检测与定位");
        m2.setModelApi("yolov8");
        m2.setStatus(1);
        m2.onCreate();
        modelInfoMapper.insert(m2);

        ModelInfo m3 = new ModelInfo();
        m3.setModelName("BERT-Base");
        m3.setModelType("text");
        m3.setAlgorithm("Transformer Encoder");
        m3.setAccuracy(89.4);
        m3.setDescription("经典文本分类模型，基于Transformer架构，支持中文情感分析");
        m3.setModelApi("bert");
        m3.setStatus(1);
        m3.onCreate();
        modelInfoMapper.insert(m3);

        ModelInfo m4 = new ModelInfo();
        m4.setModelName("XGBoost");
        m4.setModelType("ml");
        m4.setAlgorithm("Gradient Boosting");
        m4.setAccuracy(87.6);
        m4.setDescription("经典结构化数据模型，基于梯度提升树，适用于表格数据预测");
        m4.setModelApi("xgboost");
        m4.setStatus(1);
        m4.onCreate();
        modelInfoMapper.insert(m4);

        log.info("初始模型数据创建完成: 4个模型");
    }
}
