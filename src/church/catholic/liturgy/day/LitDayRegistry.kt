package church.catholic.liturgy.day

import church.catholic.liturgy.db.*
import java.time.DayOfWeek

/**
 * 전례일 정의 집합체
 *
 * 어떤 특정한 전례일 '정보'를 저장, 검색, 추가, 변경, 관리하는 역할을 한다.
 *
 * 이 class가 하는 역할은 다음과 같다.
 * - 전체적인 전례일 정보 (DayId) 캡슐화 관리 :
 * 외부에서는 DayId를 직접 사용하지 않고, 반드시 레지스트리를 통해 DayId를 검색해서 활용한다.
 *
 * - 전례일 정보 검색 : 특정 조건에 해당하는 전례일을 나열해 준다.
 *
 * - 전례일 정보 저장(추가) : 특별히 기존에 없는 신규 전례일 정보가 필요한 경우, 새로 DayId 객체를 만들어 준다.
 *
 * - 전례일 정보 변경 : 기존 전례일 정보 위에 특이사항을 오버레이한다.
 *
 *
 *
 */
class LitDayRegistry(
    private val locale: String = "la_VA"
) {

    val fixedIdList = mutableListOf<DayID>()
    val moveableIdList = mutableListOf<DayID>()

    val idList: MutableList<DayID>
    private val indexById = mutableMapOf<String, DayID>()

    init {

        updateIdFromDb()

        idList = mutableListOf<DayID>().apply {
            addAll(fixedIdList); addAll(moveableIdList)
        }

        rebuildIndex()
    }

    fun updateIdFromDb() {
        val idRepo = IdRepo()

        fixedIdList += idRepo.getIdList(DayCat.FIXED_FEAST, locale)
        moveableIdList += idRepo.getIdList(DayCat.MOVEABLE_FEAST, locale)
    }

    private fun rebuildIndex() {
        indexById.clear()
        idList.forEach { indexById[it.id] = it }
    }

    /**
     *
     *
     * 성탄 후 제2주일 검색 가능
     */
    fun findSunday(temp: LitTemp, weekNo: Int): DayID {
        val sundayGrade: LitGrade = when(temp) {

            LitTemp.ADVENTUS,
            LitTemp.QUADRAGESIMAE,
            LitTemp.PASCHALIS -> LitGrade.DOMINICA_ADV_QUAD_PASCH

            LitTemp.NATIVITATIS,
            LitTemp.PER_ANNUM -> LitGrade.DOMINICA_NAT_ANNUM
        }

        // name 필드는 `w + [2자리 주간 번호] + d + [1자리 요일 번호]` 형식이다.
        // 따라서 `w + [2자리 주간 번호]`를 먼저 만들어 준다.
        val weekNoStr = "w%02d".format(weekNo)

        return find(
            DayCat.MOVEABLE_FEAST,
            temp.temp,
            sundayGrade,
            weekNoStr + "d0"
        )
    }

    /**
     * 전례 시기의 특정 주간을 반환하는 함수
     *
     * 이 함수는
     *
     * - `[전례시기] 제[주간 번호]주일`
     * - `[전례시기] 제[주간 번호]주간 [요일]`
     *
     * 로 표시되는 전례 시기의 주간을 반환한다.
     * 따라서,
     *
     * - `성주간 파스카 성삼일`,
     * - `12월 17일 - 24일`
     *
     * 은 이 함수로 탐색할 수 없다.
     *
     * =======
     *
     * 전례 시기는 특정일을 중심으로, '주간'을 통해 이어지기에,
     * 결국 1년 52주간이 모두 전례 주간으로 가득 차게 된다.
     *
     * 전례 주간의 5자리 이름 태그는 특정일을 제외하면,
     * 모두 다음의 형식으로 저장되어 있다.
     *
     * ```w + [2자리 주간 번호] + d + [1자리 요일 번호]```
     *
     * 동시에, 전례 시기에 따라 주일과 평일의 등급이 달라진다.
     *
     * 따라서, 이 함수는 전례 시기 계산 간,
     * ID 탐색을 용이하게 해주는 함수인 것이다.
     *
     * @param temp 탐색 대상 전례 시기
     * weekNo 탐색 대상 주간
     *
     * @return 해당 주간에 속한 주간의 `DayID` 객체 `List`
     */
    fun findWeekdays(temp: LitTemp, weekNo: Int): Map<DayOfWeek, DayID?> {
        val list = mutableMapOf<DayOfWeek, DayID?>()

        val weekdayGrade: LitGrade = when(temp) {
            // 대림 시기는 첫째 부분과 둘째 부분이 있다.
            // 다만 둘째 부분은 이 함수로 탐색하지 않을 것이기 때문에,
            // 첫째 부분만 반환하도록 한다.
            LitTemp.ADVENTUS -> LitGrade.FERIAE_ADV_I
            LitTemp.NATIVITATIS -> LitGrade.FERIAE_NAT
            LitTemp.QUADRAGESIMAE -> LitGrade.FERIAE_QUAD
            LitTemp.PASCHALIS -> LitGrade.FERIAE_PASCH
            LitTemp.PER_ANNUM -> LitGrade.FERIAE_PER_ANNUM
        }

        // name 필드는 `w + [2자리 주간 번호] + d + [1자리 요일 번호]` 형식이다.
        // 따라서 `w + [2자리 주간 번호]`를 먼저 만들어 준다.
        val weekNoStr = "w%02d".format(weekNo)

        for(dow in DayOfWeek.entries) {

            list[dow] = if(dow == DayOfWeek.SUNDAY) {
                findSunday(temp, weekNo)
            } else {
                find(
                    DayCat.MOVEABLE_FEAST,
                    temp.temp,
                    weekdayGrade,
                    weekNoStr + "d" + (dow.value)
                )
            }
        }

        return list
    }

    /**
     * ID 값에 따라서 `DayID`를 반환하는 함수
     *
     */
    fun find(idStr: String): DayID {
        require(DayID.validate(idStr)) {
            "Invalid ID: $idStr"
        }
        return indexById[idStr] ?: error("Cannot find day with specified ID : $idStr")
    }

    fun find(
        cat: DayCat,
        scope: String,
        grade: LitGrade,
        name: String
    ): DayID {
        val sb = StringBuilder()
        sb
            .append(cat.cat).append(".")
            .append(scope).append(".")
            .append(grade.grade).append('.')
            .append(name)

        return find(sb.toString())
    }

    /* == 상수 구역 (구 CoreId, CoreDay) == */

    // 주님 성탄
    // 12월 17일 - 24일
    // 주님 공현
    // 주님 세례
    // 성가정 축일

    // 성목요일
    // 성금요일
    // 성토요일

    // 주님 승천

}