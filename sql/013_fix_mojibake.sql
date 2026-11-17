SET NAMES utf8mb4;  -- 见 013_fix_mojibake.sql：避免 latin1 客户端把中文写成乱码
-- =============================================================================
-- 013_fix_mojibake.sql — 修复由 latin1 客户端导入导致的「双重编码」乱码
--
-- 症状
--   界面右上角昵称显示成  è¶…çº§ç®¡ç†å‘˜ ，头像 emoji 显示成 ðŸ‘¨â€​ðŸ’¼ ；
--   模型详情里的中文描述同样变成 åŸºäºŽ ResNet-50 … 。
--
-- 病因
--   官方 mysql:8.0 镜像的 mysql 命令行客户端默认 character_set_client=latin1。
--   容器首次初始化时 /docker-entrypoint-initdb.d/*.sql 由该客户端导入，
--   文件里的 UTF-8 字节被当成 latin1 字符，再以 utf8mb4 存回，等于编码了两次：
--     超 (E8 B6 85)  →  è(E8) ¶(B6) …(85)  →  C3A8 C2B6 E280A6
--   所以肉眼看到的是「UTF-8 被当成 Latin-1 解读」的典型串。
--
-- 为什么 Agent 表没事
--   Agent 的会话、知识库分块由 JDBC（utf8mb4）写入，绕过了这个客户端，
--   因此只有 sql/001、003、005、008 里的中文字段受影响。
--
-- 为什么会发生 / 怎么彻底避免
--   sql/*.sql 每个文件首行都写了 SET NAMES utf8mb4，
--   导入时无论客户端默认是什么字符集，会话都会被切到 utf8mb4。
--   （早期尝试过挂载 mysql 配置文件，但 Windows 上 bind mount 的文件是 0777，
--     MySQL 以 world-writable 为由忽略，故改用 SET NAMES 这种文件级方案。）
--
-- 修复原理
--   latin1 → 二进制 → utf8mb4，把多编的一层编解码退回去。
--   MySQL 的 latin1 实际是 cp1252 超集，能正确表示 0x80–0x9F 区间
--   （如 … 对应 0x85），因此这种双重编码可以无损还原。
--
-- 安全性
--   每条 UPDATE 都带 HEX(...) REGEXP 前置条件，只处理确实含双重编码特征的行走；
--   正确的中文（E4B8AD…）不含 C3A5/C3A6/C3A7/C3A8/C3B0 这类序列，不会被误改。
--   修好之后再执行一遍是空操作，可安全重复运行。
--
-- 执行
--   mysql -uroot -p --default-character-set=utf8mb4 < sql/013_fix_mojibake.sql
-- =============================================================================

USE ai_modelhub;

-- ① 用户昵称：超级管理员 / 演示用户
UPDATE sys_user
SET nickname = CONVERT(CAST(CONVERT(nickname USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(nickname) REGEXP 'C3(A5|A6|A7|A8|B0)';

-- ② 用户头像 emoji：👨‍💼 / 🧑‍🔬
UPDATE sys_user
SET avatar = CONVERT(CAST(CONVERT(avatar USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(avatar) REGEXP 'C3(A5|A6|A7|A8|B0)';

-- ③ 模型中文描述（详情页、Agent 工具返回的 description）
UPDATE model_info
SET description = CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(description) REGEXP 'C3(A5|A6|A7|A8|B0)';

-- ④ 模型名称（多数为英文，若有人录入中文名同样处理）
UPDATE model_info
SET model_name = CONVERT(CAST(CONVERT(model_name USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(model_name) REGEXP 'C3(A5|A6|A7|A8|B0)';

-- ── 校验：以下两条应分别返回 0 行 / 预期中文 ────────────────────────────────
SELECT '残留乱码行数(应为 0)' AS check_item,
       (SELECT COUNT(*) FROM sys_user
         WHERE HEX(nickname) REGEXP 'C3(A5|A6|A7|A8|B0)'
            OR HEX(avatar)   REGEXP 'C3(A5|A6|A7|A8|B0)')
     + (SELECT COUNT(*) FROM model_info
         WHERE HEX(description) REGEXP 'C3(A5|A6|A7|A8|B0)') AS bad_rows;

SELECT id, username, nickname, avatar FROM sys_user ORDER BY id;
