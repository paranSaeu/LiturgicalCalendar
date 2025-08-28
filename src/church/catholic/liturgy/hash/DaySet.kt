package church.catholic.liturgy.hash

class DaySet(
    vararg litDay: LitDay,
    val litTemp: LitTemp
) {

    val daySet: MutableSet<LitDay> = mutableSetOf<LitDay>()

    init {
        for(d in litDay) {
            daySet.add(d)
        }
    }

    fun plus(otherDaySet: DaySet): DaySet{

        for(d in otherDaySet.daySet) {
            daySet.add(d)
        }

        if(this.litTemp != otherDaySet.litTemp) {
            error("Duplicated Liturgical Time: ${this.litTemp} and ${otherDaySet.litTemp}")
        }

        return this
    }


    /**
     * `DaySet`을 DB 저장용 해시로 암호화하는 함수
     *
     * `DaySet`은 각 전례일 정보 집합과 전례 시기를 저장한다.
     * 그러나 DB에 저장할 때는 단순 TEXT형으로 저장하기 때문에,
     * `DaySet`을 적절한 형식에 맞추어 암호화할 필요가 있다.
     *
     * `DaySet`은 다음 형식으로 암호화한다.
     * 1. `DaySet`의 전례일 집합 크기 + "#"
     * 2. `litTemp` 저장용 value
     * 3... "#" + 각 `litDay` ID
     * 4. End-of-Hash letter "/"
     *
     */
    fun encode(): String{
        var sb: StringBuilder = StringBuilder();

        sb.append(daySet.size).append("#")

        sb.append(this.litTemp)

        for(day: LitDay in daySet) {
            sb.append("#").append(day.encode())
        }

        sb.append("/")

        return sb.toString()
    }

    fun decode(hash: String): DaySet{

    }

}