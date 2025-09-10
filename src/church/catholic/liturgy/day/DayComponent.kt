package church.catholic.liturgy.day


enum class DayCat ( var cat: String ) {
    FIXED_FEAST             ("fix"),
    MOVEABLE_FEAST          ("mov");

    companion object {
        fun fromIdStr(id: String): DayCat =
            when(DayID.validate(id)) {
                true -> fromCatStr(id.split(".")[0])
                else -> error("Invalid Id: $id")
            }

        fun fromCatStr(cat: String): DayCat =
            entries.find { it.cat == cat } ?: error("Unknown Id Category: $cat")
    }

    override fun toString(): String {
        return this.cat
    }
}

enum class LitTemp ( var temp: String ) {
    ADVENTUS        ("adve"),
    NATIVITATIS     ("nati"),
    QUADRAGESIMAE   ("quad"),
    PASCHALIS       ("psch"),
    PER_ANNUM       ("annu"),
    NONE            ("none");

    companion object {
        fun fromIdStr(id: String): LitTemp =
            when(DayID.validate(id)) {
                true -> if(DayCat.fromIdStr(id) == DayCat.FIXED_FEAST) {
                    error("Liturgical Time not specified for fixed feast: $id")
                } else {
                    fromCode(id.split(".")[1])
                }
                else -> error("Invalid Id: $id")
            }

        fun fromCode(code: String): LitTemp =
            entries.find { it.temp == code } ?: error("Unknown Liturgical Time: $code")
    }

    override fun toString(): String {
        return this.temp
    }
}

enum class LitGrade (
    var classNum    : Int,
    var classLine   : Int,
    var mode        : Int
)
{
    TRIDUUM_PASCHALE				(1, 1, 0), // 파스카 성삼일

    FERIA_V_HEBD_SANC				(2, 0, 0), // 성목요일 (예외; 원래 240)
    NEAP							(2, 1, 0), // 주님 성탄, 공현, 승천, 성령 강림
    DOMINICA_ADV_QUAD_PASCH			(2, 2, 0), // 대림 사순 부활 시기의 주일
    PRO_DEFUNCTIS_I					(2, 2, 1), // 기원 1
    FERIA_IV_CINERUM				(2, 3, 0), // 재의 수요일
    FERIAE_HEBD_SANCTAE				(2, 4, 0), // 성주간 월-목요일
    OCT_PASCH						(2, 5, 0), // 부활 팔일 축제

    SOLLEMNITAS_GENERALIS			(3, 1, 0), // 대축일
    OMNIUM_FIDELIUM_DEFUNCTORUM		(3, 2, 0), // 위령의 날

    SOLLEMNITAS_PATRONI				(4, 1, 0), // 주요 수호자 대축일
    SOLLEMNITAS_DEDICATIONIS		(4, 2, 0), // 성당 봉헌 (주년) 대축일
    SOLLEMNITAS_TITULI				(4, 3, 0), // 성당 주보 대축일
    SOLLEMNITAS_TITULI_ORDINIS		(4, 4, 0), // 수도회 주보 대축일
    PRO_VARIIS_NECESSITATIBUS_I		(4, 5, 2), // 기원 1

    FESTUM_DOMINI					(5, 1, 0), // 주님의 축일

    DOMINICA_NAT_ANNUM				(6, 1, 0), // 성탄 연중 시기의 주일

    FESTUM_GENERALIS				(7, 1, 0), // 축일

    FESTUM_PATRONI_DIOECESIS		(8, 1, 0), // 교구 수호자 축일
    FESTUM_DEDICATIONIS				(8, 2, 0), // 주교좌성당 봉헌 축일
    FESTUM_PATRONI_REGIONIS			(8, 3, 0), // 지역 수호자 축일
    FESTUM_TITULI_ORDINIS			(8, 4, 0), // 수도회 주보 축일
    ALIUM_FESTUM_ECCLESIAE			(8, 5, 0), // 성당 고유 축일
    ALIUM_FESTUM_DIOECESIS			(8, 6, 0), // 교구, 수도회 고유 축일
    PRO_DEFUNCTIS_II				(8, 7, 1), // 위령 2

    FERIAE_ADV_II					(9, 1, 0), // 대림 시기 평일 2
    OCT_NAT							(9, 2, 0), // 성탄 팔일 축제
    FERIAE_QUAD						(9, 3, 0), // 사순 시기 평일
    PRO_VARIIS_NECESSITATIBUS_II	(9, 4, 2), // 기원 2

    MEMORIA_OBLIGATORIA				(10, 1, 0), // 의무 기념일

    MEMORIA_PATRONI					(11, 1, 0), // 수호자 기념일
    MEMORIA_PROPRIA					(11, 2, 0), // 교구, 수도회 기념일

    MEMORIA_AD_LIBITUM				(12, 1, 0), // 선택 기념일

    FERIAE_ADV_I					(13, 1, 0), // 대림 시기 평일 1
    FERIAE_NAT						(13, 2, 0), // 성탄 시기 평일
    FERIAE_PASCH					(13, 3, 0), // 부활 시기 평일
    PRO_DEFUNCTIS_III				(13, 3, 1), // 위령 3
    PRO_VARIIS_NECESSITATIBUS_III	(13, 3, 2), // 기원 3
    FERIAE_PER_ANNUM				(13, 4, 0); // 연중 시기 평일

    fun grade(): String =
        classNum.toString(16) + classLine.toString() + mode.toString()

    override fun toString(): String = this.grade()

    companion object {
        fun fromGradeStr(gradeStr: String): LitGrade {
            require(gradeStr.length == 3) { "Grade must be 3 chars: $gradeStr" }
            val s = gradeStr.lowercase()
            val cn = s[0].digitToInt(16) // 16진수 한 글자 (소문자/대문자 둘 다 허용)
            val cl = s[1].digitToInt()
            val m = s[2].digitToInt()
            return entries.find { it.classNum == cn && it.classLine == cl && it.mode == m }
                ?: error("Unknown Liturgical Grade Code: $gradeStr")
        }

        fun fromGradeInt(gradeInt: Int): LitGrade =
            fromGradeStr(String.format("%03x", gradeInt)) // 소문자 hex

        fun fromIdStr(id: String): LitGrade {
            require(DayID.validate(id)) { "Invalid Id: $id" }
            val gradeToken = id.split(".")[2]
            return fromGradeStr(gradeToken)
        }
    }
}

enum class LitColourValue ( val bit: Int ) {
    WHITE   (0b00000001),
    RED     (0b00000010),
    GREEN   (0b00000100),
    PURPLE  (0b00001000),
    BLACK   (0b00010000),
    PINK    (0b00100000),
    SKY_BLUE(0b01000000),
    ETC     (0b10000000);
}

@JvmInline
value class LitColour( val colour: Int ) {
    fun contains(c: LitColourValue): Boolean = (colour and c.bit) != 0

    fun toSet(): Set<LitColourValue> =
        LitColourValue.entries.filterTo(mutableSetOf()) { contains(it) }

    infix fun or(other: LitColour)  = LitColour(this.colour or other.colour)
    infix fun or(c: LitColourValue) = LitColour(this.colour or c.bit)

    companion object {
        fun of(vararg colours: LitColourValue): LitColour =
            LitColour(colours.fold(0) { acc, c -> acc or c.bit })

        fun from(colourNum: Int) = LitColour(colourNum)
    }
}