package church.catholic.liturgy.db

import church.catholic.liturgy.day.*
import java.sql.*
import javax.xml.crypto.Data

object DatabaseManager {
    private val handles = mutableMapOf<DbKey, DbHandle>()
    private val baseDir: java.nio.file.Path by lazy { resolveBaseDir() }

    // 스펙 레지스트리 (DB 종류별 파일명/스키마 초기화)
    private val specs: Map<DbKind, DbSpec> = mapOf(
        DbKind.ID_LIST to DbSpec(
            fileNameBuilder = { "liturgical_id_list.db" },
            initSchema = { c ->
                c.createStatement().use { st ->
                    st.execute("PRAGMA foreign_keys = ON;")
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS fixed_feast (
                          id TEXT PRIMARY KEY,
                          id_cat TEXT NOT NULL,
                          scope TEXT NOT NULL,          -- 0101-1231 // DATE OF THE DAY
                          lit_grade TEXT NOT NULL,
                          lit_colour TEXT NOT NULL,
                          id_name TEXT NOT NULL,
                          la_VA TEXT NOT NULL,
                          en_US TEXT,
                          ko_KR TEXT
                        );
                    """.trimIndent())
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS moveable_feast (
                          id TEXT PRIMARY KEY,
                          id_cat TEXT NOT NULL,
                          scope TEXT NOT NULL,          -- adve/nati/quad/psch/annu // SEASON OF THE DAY
                          lit_grade TEXT NOT NULL,
                          lit_colour TEXT NOT NULL,
                          id_name TEXT NOT NULL,
                          la_VA TEXT NOT NULL,
                          en_US TEXT,
                          ko_KR TEXT
                        );
                    """.trimIndent())
                }
            }
        ) /*,
        DbKind.ORDO to DbSpec(
            fileNameBuilder = { "textum_ordo.db" },
            initSchema = { c ->
                c.createStatement().use { st ->
                    st.execute("PRAGMA foreign_keys = ON;")
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS ordo (
                          id TEXT PRIMARY KEY,      -- DayID.id
                          json TEXT NOT NULL
                        );
                    """.trimIndent())
                }
            }
        ),
        DbKind.MISSAL to DbSpec(
            fileNameBuilder = { key ->
                if (key.lang == null) "textum_missalis.db"
                else "textum_missalis_${key.lang}.db"
            },
            initSchema = { c ->
                c.createStatement().use { st ->
                    st.execute("PRAGMA foreign_keys = ON;")
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS missal (
                          id TEXT PRIMARY KEY,  -- DayID.id
                          json TEXT NOT NULL
                        );
                    """.trimIndent())
                }
            }
        ),
        DbKind.LH to DbSpec(
            fileNameBuilder = { key ->
                if (key.lang == null) "textum_liturgiae_horarum.db"
                else "textum_liturgiae_horarum_${key.lang}.db"
            },
            initSchema = { c ->
                c.createStatement().use { st ->
                    st.execute("PRAGMA foreign_keys = ON;")
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS lh (
                          id TEXT PRIMARY KEY,  -- DayID.id
                          json TEXT NOT NULL
                        );
                    """.trimIndent())
                }
            }
        ) */
    )

    /** 핸들 얻기(없으면 열고 스키마 보장) */
    @Synchronized
    fun get(key: DbKey): DbHandle {
        handles[key]?.let { if (it.isOpen()) return it }

        val spec = specs[key.kind] ?: error("No spec for $key")
        val file = baseDir.resolve(spec.fileNameBuilder(key))
        java.nio.file.Files.createDirectories(file.parent)

        val url = "jdbc:sqlite:${file.toAbsolutePath()}"

        println(url)

        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection(url).apply {
            createStatement().use { st ->
                st.execute("PRAGMA journal_mode = WAL;")
                st.execute("PRAGMA synchronous = NORMAL;")
            }
        }

        // 스키마 초기화
        spec.initSchema(conn)

        return DbHandle(key, conn).also { handles[key] = it }
    }

    fun close(key: DbKey) {
        handles.remove(key)?.conn?.close()
    }

    fun closeAll() {
        handles.values.forEach { it.conn.close() }
        handles.clear()
    }

    private fun resolveBaseDir(): java.nio.file.Path {
        // 실행 디렉토리 (일반적으로 프로젝트 루트)
        val root = System.getProperty("user.dir")
        return java.nio.file.Paths.get(root, "db")
    }
}

enum class DbKind {
    ID_LIST,         // liturgical_id_list.db
    ORDO,            // textum_ordo.db
    MISSAL,          // textum_missalis.db
    LH               // textum_liturgiae_horarum.db
}

data class DbKey(
    val kind: DbKind,
    val lang: String = "la_VA"   // 언어 없는 DB는 라틴 말, 언어별 DB는 "ko_KR" 등
)

data class DbSpec(
    val fileNameBuilder: (DbKey) -> String,               // 파일명 규칙
    val initSchema: (Connection) -> Unit         // 스키마 초기화(필요 시)
)

class DbHandle(
    val key: DbKey,
    val conn: Connection
) {
    fun isOpen(): Boolean = conn.isValid(1)

    fun exec(sql: String, bind: (PreparedStatement) -> Unit = {}): Int {
        conn.prepareStatement(sql).use { ps ->
            bind(ps); return ps.executeUpdate()
        }
    }

    fun <T> query(
        sql: String,
        bind: (PreparedStatement) -> Unit = {},
        map: (ResultSet) -> T
    ): List<T> {
        conn.prepareStatement(sql).use { ps ->
            bind(ps)
            ps.executeQuery().use { rs ->
                val out = mutableListOf<T>()
                while (rs.next()) out += map(rs)
                return out
            }
        }
    }
}

class IdRepo(private val db: DatabaseManager = DatabaseManager) {
    private val key = DbKey(DbKind.ID_LIST)

    fun getIdList(cat: DayCat, locale: String): List<DayID> {

        val table: String = when(cat) {
            DayCat.FIXED_FEAST -> "fixed_feast"
            DayCat.MOVEABLE_FEAST -> "moveable_feast"
        }

        val localeColumn: String = when(locale) {
            "la_VA",
            "en_US",
            "ko_KR" -> locale

            else -> error("Unavailable Locale : $locale")
        }

        val sql = """
            SELECT id, id_cat, scope, lit_grade, lit_colour, $localeColumn AS name
            FROM $table
            ORDER BY id ASC
        """.trimIndent()

        return db.get(key).query(sql, map = {rs ->
            mapFixedRowToDayID(rs)
        })
    }

    private fun mapFixedRowToDayID(rs: ResultSet): DayID {
        val id      = rs.getString("id")
        val cat     = rs.getString("id_cat")
        val scope   = rs.getString("scope")
        val grade   = rs.getString("lit_grade")
        val colours = rs.getString("lit_colour")
        val name    = rs.getString("name")

        return DayID(
            id      = id,
            cat     = DayCat.fromCatStr(cat),
            scope   = scope,
            grade   = LitGrade.fromGradeInt(grade.toInt()),
            colour  = LitColour.from(colours.toInt()),
            name    = name
        )
    }
}