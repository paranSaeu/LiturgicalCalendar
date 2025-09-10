package church.catholic.liturgy.day

data class DayID(
    val id      : String,     // ID
    val cat     : DayCat,     // fixed or moveable
    val scope   : String,     // date or temp
    val grade   : LitGrade,   // grade
    val colour  : LitColour,  // colour
    val name    : String      // full name
) {
    val idName: String

    init {
        validate()

        // 유효한 ID 값이어야 idName을 초기화할 수 있음
        idName =  id.split(".")[3]
    }

    constructor(
        id: String,
        colour: LitColour,
        name: String):
            this(
        id      = id,
        cat     = parseParts(id).cat,
        scope   = parseParts(id).scope,
        grade   = parseParts(id).grade,
        colour  = colour,
        name    = name
    )

    override fun toString(): String = name

    fun validate() {
        require(validate(this.id)) {
            "Invalid ID: ${this.id}"
        }

        val parsedGrade = LitGrade.fromIdStr(id)
        require(this.grade == parsedGrade) {
            "ID not correct: $id does not contain $grade (parsed=$parsedGrade)"
        }
    }

    companion object {

        private val ID_REGEX = Regex(
            pattern = "^(?:fix\\.(\\d{2})(\\d{2})|mov\\.(adve|nati|quad|psch|annu))\\.[0-9A-Fa-f]{3}\\.[a-z0-9]{5}\$"
        )

        fun validate(targetStr: String): Boolean {
            // 총 18자 고정(성능 미세 최적화 & 빠른 거절)
            if (targetStr.length != 18) return false

            val m = ID_REGEX.matchEntire(targetStr) ?: return false

            // 그룹:
            // 1: MM (fix일 때만 존재)
            // 2: dd (fix일 때만 존재)
            // 3: mov scope 키워드(adve/nati/quad/psch/annu)
            val mmStr = m.groups[1]?.value
            val ddStr = m.groups[2]?.value
            val movScope = m.groups[3]?.value

            return if (mmStr != null && ddStr != null) {
                // fix.* 인 경우 → MMdd 유효성 검사
                val mm = mmStr.toInt()
                val dd = ddStr.toInt()
                isValidMonthDay(mm, dd)
            } else {
                // mov.* 인 경우 → 정규식에서 이미 키워드 보장
                movScope != null
            }
        }

        /** ID를 토큰으로 안전하게 분해해서 cat/scope 를 얻는다. */
        fun parseParts(id: String): Parsed {
            require(validate(id)) { "Invalid ID: $id" }

            val parts = id.split(".")
            require(parts.size == 4) { "ID must have 4 tokens: $id" }
            val catToken   = parts[0]          // "fix" or "mov"
            val scopeToken = parts[1]          // "1225" or "psch"
            val gradeToken = parts[2]          // "210" (hex)
            val nameToken  = parts[3]          // "natdo"

            val cat = DayCat.fromCatStr(catToken)

            // 선택: scope 일관성 추가 점검(정규식이 이미 보장하지만, 방어적으로 유지)
            when (cat) {
                DayCat.FIXED_FEAST -> require(scopeToken.length == 4 && scopeToken.all { it.isDigit() }) {
                    "FIX scope must be 4 digits MMdd: $id"
                }
                DayCat.MOVEABLE_FEAST -> require(scopeToken in setOf("adve", "nati", "quad", "psch", "annu")) {
                    "MOV scope must be one of adve/nati/quad/psch/annu: $id"
                }
            }
            // 등급/이름 형식도 여기서 가볍게 재확인 가능
            require(gradeToken.length == 3 && gradeToken.all { it in "0123456789ABCDEFabcdef" }) {
                "Grade must be 3 hex chars: $id"
            }
            val grade = LitGrade.fromGradeStr(gradeToken)

            require(nameToken.length == 5 && nameToken.all { it.isLowerCase() || it.isDigit() }) {
                "Name must be 5 lowercase/digits: $id"
            }

            return Parsed(cat, scopeToken, grade)
        }

        // DTO for parsing result
        data class Parsed(
            val cat: DayCat,
            val scope: String,
            val grade: LitGrade
        )

        // 월별 일수 체크 (2월 29일 허용 여부: 필요에 따라 true/false 조정)
        private fun isValidMonthDay(mm: Int, dd: Int): Boolean {
            if (mm !in 1..12) return false
            val maxDay = when (mm) {
                1,3,5,7,8,10,12 -> 31
                4,6,9,11        -> 30
                2               -> 29 // 2월 29일을 허용(고정 축일로 쓸 일은 거의 없지만 설계상 유연성 부여)
                else            -> return false
            }
            return dd in 1..maxDay
        }

        fun of(
            cat: DayCat,
            scope: String,      // "1225" or "psch"
            grade: LitGrade,
            colour: LitColour,
            idName: String,     // 5 chars
            name: String        // full name
        ): DayID {

            // scope 적합성 방어(문자열 조립 전 한 번 더 체크)
            when (cat) {
                DayCat.FIXED_FEAST -> require(scope.length == 4 && scope.all { it.isDigit() }) {
                    "FIX scope must be 4 digits MMdd, got: $scope"
                }
                DayCat.MOVEABLE_FEAST -> require(scope in setOf("adve", "nati", "quad", "psch", "annu")) {
                    "MOV scope must be adve/nati/quad/psch/annu, got: $scope"
                }
            }

            val id = buildString {
                append(cat.cat).append(".")
                append(scope).append(".")
                append(grade.toString()).append(".")
                append(idName)
            }
            return DayID(id, colour, name) // 보조 생성자 경유 → 파싱/검증 일원화
        }
    }

}