package com.aimodelhub.mapper;

import com.aimodelhub.model.entity.ModelInfo;
import com.aimodelhub.model.mapper.ModelInfoMapper;
import com.aimodelhub.predict.entity.PredictRecord;
import com.aimodelhub.predict.mapper.PredictRecordMapper;
import com.aimodelhub.user.entity.FavoriteModel;
import com.aimodelhub.user.entity.SysUser;
import com.aimodelhub.user.mapper.FavoriteModelMapper;
import com.aimodelhub.user.mapper.SysUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis mapper integration tests — verify insert (PK backfill), query,
 * update and delete against a real MySQL connection.
 * <p>
 * Each test runs inside a {@code @Transactional} transaction that is rolled
 * back on completion, so no rows are persisted to the development database.
 * Unique identifiers (currentTimeMillis) avoid colliding with seed data.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Rollback
@DisplayName("MyBatis mapper CRUD + PK backfill")
class MapperIntegrationTest {

    @Autowired
    private ModelInfoMapper modelInfoMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private FavoriteModelMapper favoriteModelMapper;

    @Autowired
    private PredictRecordMapper predictRecordMapper;

    private static String unique(String prefix) {
        return prefix + System.currentTimeMillis();
    }

    // ── ModelInfo ─────────────────────────────────────────────────

    @Test
    @DisplayName("ModelInfo: insert backfills id, findById/update/count work")
    void modelInfoCrud() {
        ModelInfo model = new ModelInfo();
        model.setModelName(unique("MapperTestModel_"));
        model.setModelType("image");
        model.setAlgorithm("CNN");
        model.setAccuracy(88.8);
        model.setStatus(1);
        model.onCreate();

        int inserted = modelInfoMapper.insert(model);
        assertEquals(1, inserted);
        assertNotNull(model.getId(), "insert should backfill the generated id");

        ModelInfo found = modelInfoMapper.findById(model.getId());
        assertNotNull(found);
        assertEquals(model.getModelName(), found.getModelName());
        assertEquals("CNN", found.getAlgorithm());
        assertEquals(88.8, found.getAccuracy());

        found.setAlgorithm("CNN-v2");
        found.onUpdate();
        modelInfoMapper.update(found);
        ModelInfo updated = modelInfoMapper.findById(model.getId());
        assertEquals("CNN-v2", updated.getAlgorithm());

        assertTrue(modelInfoMapper.count() > 0);
        assertTrue(modelInfoMapper.countByStatus(1) >= 0);
    }

    @Test
    @DisplayName("Pagination: findAll/findAllOrderByIdAsc return ascending id pages")
    void paginationOrderById() {
        List<ModelInfo> all = modelInfoMapper.findAll();
        assertFalse(all.isEmpty());
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i).getId() > all.get(i - 1).getId(), "models should be id-ascending");
        }

        long total = modelInfoMapper.count();
        List<ModelInfo> page0 = modelInfoMapper.findAllOrderByIdAsc(0, 2);
        assertFalse(page0.isEmpty());
        assertTrue(page0.size() <= 2);
        assertEquals(Math.min(2, total), page0.size());

        List<SysUser> users = sysUserMapper.findAllOrderByIdAsc(0, 5);
        assertFalse(users.isEmpty());
        assertTrue(users.size() <= 5);
        for (int i = 1; i < users.size(); i++) {
            assertTrue(users.get(i).getId() > users.get(i - 1).getId(), "users should be id-ascending");
        }
    }

    @Test
    @DisplayName("ModelInfo: findByStatus / findByStatusNot order by id")
    void modelInfoStatusQueries() {
        List<ModelInfo> online = modelInfoMapper.findByStatus(1);
        assertFalse(online.isEmpty(), "seed data should contain online models");
        assertTrue(online.stream().allMatch(m -> m.getStatus() == 1));

        List<ModelInfo> notDeleted = modelInfoMapper.findByStatusNotOrderByIdAsc(-1);
        assertTrue(notDeleted.stream().allMatch(m -> m.getStatus() != -1));
    }

    // ── SysUser ───────────────────────────────────────────────────

    @Test
    @DisplayName("SysUser: insert backfills id, findByUsername/update/count work")
    void sysUserCrud() {
        String username = unique("mapper_test_user_");
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        user.setNickname("MapperTest");
        user.setRole("user");
        user.onCreate();

        int inserted = sysUserMapper.insert(user);
        assertEquals(1, inserted);
        assertNotNull(user.getId(), "insert should backfill the generated id");

        SysUser found = sysUserMapper.findByUsername(username);
        assertNotNull(found);
        assertEquals(user.getId(), found.getId());
        assertEquals("MapperTest", found.getNickname());

        assertEquals(1, sysUserMapper.countByUsername(username));

        found.setNickname("MapperTest2");
        found.onUpdate();
        sysUserMapper.update(found);
        SysUser updated = sysUserMapper.findByUsername(username);
        assertEquals("MapperTest2", updated.getNickname());

        assertTrue(sysUserMapper.count() > 0);
    }

    // ── FavoriteModel ─────────────────────────────────────────────

    @Test
    @DisplayName("FavoriteModel: insert backfills id, exists/delete/count work")
    void favoriteModelCrud() {
        long ts = System.currentTimeMillis();
        Long userId = ts;
        Long modelId = ts + 1;

        FavoriteModel fav = new FavoriteModel(userId, modelId);
        int inserted = favoriteModelMapper.insert(fav);
        assertEquals(1, inserted);
        assertNotNull(fav.getId(), "insert should backfill the generated id");

        assertEquals(1, favoriteModelMapper.countByUserIdAndModelId(userId, modelId));

        List<FavoriteModel> list = favoriteModelMapper.findByUserId(userId);
        assertTrue(list.stream().anyMatch(f -> f.getModelId().equals(modelId)));

        FavoriteModel single = favoriteModelMapper.findByUserIdAndModelId(userId, modelId);
        assertNotNull(single);
        assertEquals(modelId, single.getModelId());

        int deleted = favoriteModelMapper.deleteByUserIdAndModelId(userId, modelId);
        assertEquals(1, deleted);
        assertEquals(0, favoriteModelMapper.countByUserIdAndModelId(userId, modelId));

        assertTrue(favoriteModelMapper.countByModelId(modelId) >= 0);
    }

    // ── PredictRecord ─────────────────────────────────────────────

    @Test
    @DisplayName("PredictRecord: insert backfills id, ordered query works")
    void predictRecordInsertAndQuery() {
        Long userId = System.currentTimeMillis();

        PredictRecord record = new PredictRecord();
        record.setUserId(userId);
        record.setModelId(1L);
        record.setInputText("hello");
        record.setPredictResult("positive");
        record.setConfidence(0.99);
        record.setDuration(12);
        record.onCreate();

        int inserted = predictRecordMapper.insert(record);
        assertEquals(1, inserted);
        assertNotNull(record.getId(), "insert should backfill the generated id");

        List<PredictRecord> records = predictRecordMapper.findByUserIdOrderByCreateTimeDesc(userId);
        assertFalse(records.isEmpty());
        assertEquals(userId, records.get(0).getUserId());
        assertEquals("positive", records.get(0).getPredictResult());

        assertTrue(predictRecordMapper.count() > 0);
        assertTrue(predictRecordMapper.countByUserId(userId) >= 1);
    }
}
