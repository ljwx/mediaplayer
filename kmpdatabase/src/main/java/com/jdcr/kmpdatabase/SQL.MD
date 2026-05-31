# SQLDelight .sq 文件使用指南

## 一、SQLite 类型与 Kotlin 类型映射

| SQLite 类型 | Kotlin 类型 | 说明 |
|-------------|-------------|------|
| `INTEGER` | `Long` | 整数类型（包括 INT、BIGINT、SMALLINT 等） |
| `TEXT` | `String` | 文本类型（包括 VARCHAR、CHAR、CLOB 等） |
| `REAL` | `Double` | 浮点类型（包括 FLOAT、DOUBLE、NUMERIC、DECIMAL 等） |
| `BLOB` | `ByteArray` | 二进制类型 |
| `DATE` | `String` | SQLite 无原生 DATE，通常存为 TEXT 格式 |
| `BOOLEAN` | 不支持 | SQLite 无原生布尔，用 `INTEGER`（0/1）代替 |

### 类型别名

SQLite 支持类型别名，以下都会映射为 `Long`：
- `INT`、`BIGINT`、`SMALLINT`、`TINYINT`、`MEDIUMINT`
- `INT2`、`INT8`

以下都会映射为 `String`：
- `VARCHAR(n)`、`CHAR(n)`、`NCHAR`、`NVARCHAR`
- `CLOB`、`NCLOB`

以下都会映射为 `Double`：
- `FLOAT`、`DOUBLE`、`NUMERIC`、`DECIMAL`

---

## 二、SQL 语句定义语法

```sql
-- 基本格式：
-- 方法名:
-- SQL 语句（参数用 ? 占位）

-- 插入
insertTask:
INSERT INTO task(title, completed)
VALUES (?, ?);

-- 查询
selectAll:
SELECT * FROM task
WHERE completed = ?;

-- 更新
updateCompleted:
UPDATE task
SET completed = ?
WHERE id = ?;

-- 删除
deleteTask:
DELETE FROM task
WHERE id = ?;
```

---

## 三、常用 SQL 语句示例

### 1. INSERT

```sql
-- 普通插入
insertMedia:
INSERT INTO media_list(file_name, file_path, file_size)
VALUES (?, ?, ?);

-- 插入并忽略冲突
insertOrIgnore:
INSERT OR IGNORE INTO media_list(uuid, file_name)
VALUES (?, ?);

-- 插入或替换
insertOrReplace:
INSERT OR REPLACE INTO media_list(id, file_name)
VALUES (?, ?);
```

### 2. SELECT

```sql
-- 按条件查询
selectById:
SELECT * FROM media_list WHERE id = ?;

-- 模糊查询（LIKE）
searchByTitle:
SELECT * FROM media_list
WHERE title LIKE ?;

-- 范围查询
selectBySizeRange:
SELECT * FROM media_list
WHERE file_size BETWEEN ? AND ?;

-- IN 查询
selectByIds:
SELECT * FROM media_list
WHERE id IN ?;

-- 排序 + 分页
selectWithPagination:
SELECT * FROM media_list
ORDER BY create_time DESC
LIMIT ? OFFSET ?;

-- 聚合查询
countByCategory:
SELECT COUNT(*) FROM media_list
WHERE category = ?;

-- 多表 JOIN
selectMediaWithAuthor:
SELECT m.*, a.name as author_name
FROM media_list m
JOIN author a ON m.author = a.id
WHERE m.id = ?;
```

### 3. UPDATE

```sql
-- 按条件更新
updateTitle:
UPDATE media_list
SET title = ?
WHERE id = ?;

-- 批量更新
updateStatusByIds:
UPDATE media_list
SET status = ?
WHERE id IN ?;

-- 递增/递减
incrementPlayCount:
UPDATE media_list
SET play_count = play_count + 1
WHERE id = ?;
```

### 4. DELETE

```sql
-- 按 ID 删除
deleteById:
DELETE FROM media_list WHERE id = ?;

-- 批量删除
deleteByIds:
DELETE FROM media_list WHERE id IN ?;

-- 按条件删除
deleteByStatus:
DELETE FROM media_list WHERE status = ?;
```

---

## 四、高级用法

### 1. 自定义返回列（mapper）

```sql
-- 只返回部分字段
selectTitleAndPath:
SELECT file_name, file_path FROM media_list
WHERE id = ?;
```

生成的方法会自动返回对应的数据类。

### 2. 事务操作（在 Kotlin 中调用）

```kotlin
db.mediaListQueries.transaction {
    db.mediaListQueries.insertMedia("file1.mp4", "/path/1", 1024L)
    db.mediaListQueries.insertMedia("file2.mp4", "/path/2", 2048L)
}
```

### 3. 响应式查询（需引入 coroutines-extensions）

```kotlin
db.mediaListQueries.selectAll(null)
    .asFlow()
    .mapToList(Dispatchers.IO)
    .collect { list ->
        // 数据变化时自动触发
    }
```

### 4. 可选参数

```sql
-- 使用 ?1, ?2 命名参数（SQLDelight 1.x 不支持命名参数，需用位置参数）
-- 但可以用 NULL 判断实现可选条件
searchWithOptionalTitle:
SELECT * FROM media_list
WHERE (?1 IS NULL OR title LIKE ?1);
```

Kotlin 调用：
```kotlin
// 不筛选标题
db.mediaListQueries.searchWithOptionalTitle(null).executeAsList()

// 筛选标题
db.mediaListQueries.searchWithOptionalTitle("%关键词%").executeAsList()
```

---

## 五、注意事项

1. **方法名不能重复**，同一个 `.sq` 文件中每个查询的方法名必须唯一
2. **参数顺序很重要**，`?` 按出现顺序对应方法参数
3. **INTEGER 映射为 Long**，不是 Int，调用时要用 `1L` 而不是 `1`
4. **没有 Boolean 类型**，用 `INTEGER`（0/1）代替
5. **修改 `.sq` 后需要重新构建**才能生成新代码
6. **`.sq` 文件路径必须与 packageName 一致**
