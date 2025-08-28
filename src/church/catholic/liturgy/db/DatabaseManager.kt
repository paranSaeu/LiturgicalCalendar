package church.catholic.liturgy.db

import java.io.Closeable
import java.sql.*

class DatabaseManager(private val jdbcUrl: String) : Closeable {

    private var conn: Connection? = null

    /** 1) 연결 시작 */
    fun open() {
        if (isOpen()) return
        // 예: jdbcUrl = "jdbc:sqlite:liturgy.db"
        conn = DriverManager.getConnection(jdbcUrl).apply {
            autoCommit = true
        }
    }

    /** 2) 연결 확인 */
    fun isOpen(): Boolean = (conn?.isClosed == false)

    /** 3) 연결 종료 */
    override fun close() {
        conn?.close()
        conn = null
    }

    /** 4) 스키마 초기화(필요 테이블 없으면 생성) */
    fun initSchema() {
        require(isOpen()) { "DB is not open. Call open() first." }
        val sql = """
            CREATE TABLE IF NOT EXISTS liturgical_day(
              tag          TEXT PRIMARY KEY,
              rank_code    TEXT NOT NULL,
              name_korean  TEXT
            );
        """.trimIndent()
        exec(sql) // 단순 실행
    }

    /** 5) SELECT 쿼리 (간단 헬퍼) */
    fun <T> query(
        sql: String,
        bind: (PreparedStatement) -> Unit = {},
        map: (ResultSet) -> T
    ): List<T> {
        require(isOpen()) { "DB is not open. Call open() first." }
        val list = mutableListOf<T>()
        connection().prepareStatement(sql).use { ps ->
            bind(ps)                     // 파라미터 바인딩
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    list.add(map(rs))    // 한 행을 T로 매핑
                }
            }
        }
        return list
    }

    /** 6) INSERT/UPDATE/DELETE (영향받은 행 수 반환) */
    fun exec(
        sql: String,
        bind: (PreparedStatement) -> Unit = {}
    ): Int {
        require(isOpen()) { "DB is not open. Call open() first." }
        connection().prepareStatement(sql).use { ps ->
            bind(ps)
            return ps.executeUpdate()
        }
    }

    /** 7) 트랜잭션 실행 */
    fun <T> inTransaction(block: (Connection) -> T): T {
        require(isOpen()) { "DB is not open. Call open() first." }
        val c = connection()
        val old = c.autoCommit
        c.autoCommit = false
        return try {
            val result = block(c)
            c.commit()
            result
        } catch (t: Throwable) {
            c.rollback()
            throw t
        } finally {
            c.autoCommit = old
        }
    }

    /** 내부 연결 가져오기 */
    private fun connection(): Connection =
        conn ?: error("Connection is null. Call open().")
}
