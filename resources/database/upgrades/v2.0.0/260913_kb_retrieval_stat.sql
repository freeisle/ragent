-- v2.0.0 260913 知识库检索命中统计（Dashboard 知识库命中率卡片数据源）
-- 口径：一次提问（一条助手回答）中该知识库出现在检索结果里记 1 行；跨子问题/通道去重（每库每消息至多一行）
-- cited 标记该次回答的来源引用（sources）是否引用了该库任一文档；命中率 = SUM(cited) / COUNT(*)
-- 追加型埋点表：应用侧只写不删，不设逻辑删除列（惯例参照 t_agent_context_compaction / t_knowledge_document_chunk_log）
-- 全部语句可重复执行

CREATE TABLE IF NOT EXISTS t_kb_retrieval_stat (
    id                  VARCHAR(20) NOT NULL PRIMARY KEY,
    kb_id               VARCHAR(20) NOT NULL,
    conversation_id     VARCHAR(20) NOT NULL,
    user_id             VARCHAR(20) NOT NULL,
    task_id             VARCHAR(20) NOT NULL,
    question_message_id VARCHAR(20),
    cited               SMALLINT    NOT NULL DEFAULT 0,
    create_time         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kb_retrieval_stat_kb_time
    ON t_kb_retrieval_stat (kb_id, create_time);
COMMENT ON TABLE t_kb_retrieval_stat IS '知识库检索命中统计：每行 = 一次提问对单个知识库的一次检索命中（按提问去重）';
COMMENT ON COLUMN t_kb_retrieval_stat.kb_id IS '被命中知识库 ID（t_knowledge_base.id）';
COMMENT ON COLUMN t_kb_retrieval_stat.task_id IS '一次回答的流式任务 ID（雪花，每提问唯一），同提问跨子问题/通道的命中已合并去重';
COMMENT ON COLUMN t_kb_retrieval_stat.question_message_id IS '提问消息 ID（t_message.id），排障用，可为空';
COMMENT ON COLUMN t_kb_retrieval_stat.cited IS '该次回答 sources 是否引用该库任一文档：1-是，0-否';
COMMENT ON COLUMN t_kb_retrieval_stat.create_time IS '埋点时间（统计窗口按此列过滤）';
