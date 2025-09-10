package church.catholic.liturgy.day

import church.catholic.liturgy.db.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

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
    val fixedDateMap = CalendarMapper<MonthDay>()

    val moveableIdList = mutableListOf<DayID>()

    val idList: MutableList<DayID>
    private val indexById = mutableMapOf<String, DayID>()

    init {

        updateIdFromDb()

        idList = mutableListOf<DayID>().apply {
            addAll(fixedIdList); addAll(moveableIdList)
        }

        fixedIdList.forEach { it ->

            val month: Int = it.scope.take(2).toInt()
            val day  : Int = it.scope.takeLast(2).toInt()

            val md = MonthDay.of(month, day)

            fixedDateMap.update(md, it, litTemp = LitTemp.NONE)
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

            LitTemp.PASCHALIS -> when(weekNo) {
                1 -> LitGrade.TRIDUUM_PASCHALE              // 주님 부활 대축일 검증
                8 -> LitGrade.NEAP                          // 성령 강림 대축일 검증
                else -> LitGrade.DOMINICA_ADV_QUAD_PASCH
            }

            LitTemp.ADVENTUS,
            LitTemp.QUADRAGESIMAE -> LitGrade.DOMINICA_ADV_QUAD_PASCH

            LitTemp.NATIVITATIS,
            LitTemp.PER_ANNUM -> LitGrade.DOMINICA_NAT_ANNUM

            else -> error("Cannot find Sunday in None time")
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

        // name 필드는 `w + [2자리 주간 번호] + d + [1자리 요일 번호]` 형식이다.
        // 따라서 `w + [2자리 주간 번호]`를 먼저 만들어 준다.
        val weekNoStr = "w%02d".format(weekNo)

        try {
            val weekdayGrade: LitGrade = when(temp) {
                // 대림 시기는 첫째 부분과 둘째 부분이 있다.
                // 다만 둘째 부분은 이 함수로 탐색하지 않을 것이기 때문에,
                // 첫째 부분만 반환하도록 한다.
                LitTemp.ADVENTUS -> LitGrade.FERIAE_ADV_I
                LitTemp.NATIVITATIS -> LitGrade.FERIAE_NAT
                LitTemp.QUADRAGESIMAE -> when(weekNo) {
                    0 -> throw Exception("재의 수요일 주간 예외 처리")
                    6 -> throw Exception("성주간 예외 처리")
                    else -> LitGrade.FERIAE_QUAD
                }
                LitTemp.PASCHALIS -> when(weekNo) {
                    1 -> LitGrade.OCT_PASCH // 부활 팔일 축제
                    else -> LitGrade.FERIAE_PASCH
                }
                LitTemp.PER_ANNUM -> LitGrade.FERIAE_PER_ANNUM
                else -> error("Cannot find weekdays in None time")
            }

            for(dow in DayOfWeek.entries) {

                list[dow] = if(dow == DayOfWeek.SUNDAY) {
                    try {
                        findSunday(temp, weekNo)
                    } catch(t: Throwable) {
                        //t.printStackTrace()
                        null
                    }
                } else {
                    try {
                        find(
                            DayCat.MOVEABLE_FEAST,
                            temp.temp,
                            weekdayGrade,
                            weekNoStr + "d" + (dow.value)
                        )
                    } catch(t: Throwable) {
                        //t.printStackTrace()
                        null
                    }
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()

            if(weekNo == 0) {
                // 재의 수요일 주간은 일반적인 검색을 통해 검색하기 어렵기 때문에,
                // 직접 입력해 준다.

                for(dow in DayOfWeek.entries) {
                    list[dow] = when(dow) {
                        DayOfWeek.SUNDAY,
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY -> null

                        else -> find(
                            DayCat.MOVEABLE_FEAST,
                            temp.temp,
                            if(dow == DayOfWeek.WEDNESDAY) LitGrade.FERIA_IV_CINERUM else LitGrade.FERIAE_QUAD,
                            weekNoStr + "d" + dow.value
                        )
                    }
                }

            } else if (weekNo == 6) {
                // 성주간은 일반적인 검색을 통해 검색하기 어렵기 때문에,
                // 직접 입력해 준다.

                for(dow in DayOfWeek.entries) {
                    list[dow] = when(dow) {
                        DayOfWeek.SUNDAY    -> findSunday(temp, weekNo) // 주님 수난 성지 주일은 일반적으로 검색된다.
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY  -> find(
                            DayCat.MOVEABLE_FEAST,
                            temp.temp,
                            if(dow == DayOfWeek.THURSDAY) LitGrade.FERIA_V_HEBD_SANC else LitGrade.FERIAE_HEBD_SANCTAE,
                            weekNoStr + "d" + dow.value
                        )
                        else -> null
                    }
                }
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
            .append(grade.toString()).append('.')
            .append(name)

        return find(sb.toString())
    }

    fun find(coreConst: Core?): DayID {
        return find(coreConst!!.getId())
    }

    fun findDay(date: LocalDate): DayHash?{

        val md: MonthDay = MonthDay.of(
            date.month,
            date.dayOfMonth
        )

        return try {
            fixedDateMap.find(md)
        } catch (e: NoSuchElementException) {
            null
        }
    }

    /* == 상수 구역 (구 CoreId, CoreDay) == */

    class Core(
        private val cat     : DayCat,
        private val scope   : String,
        private val grade   : LitGrade,
        private val id_name : String
    ) {
        constructor(idStr: String) : this(
            DayCat.fromIdStr(idStr),
            idStr.split(".")[1],
            LitGrade.fromIdStr(idStr),
            idStr.split(".")[3]
        )

        fun getId(): String {
            return buildString {
                append(cat.toString()).append(".")
                append(scope).append(".")
                append(grade.toString()).append(".")
                append(id_name)
            }
        }

        companion object {

            // 대림 시기 둘째 부분 Map (12월 17일 - 24일)
            val FERIAE_ADV_II = {
                val array = mutableMapOf<Int, Core>()

                for (day in 17..24) {
                    array[day] = Core(
                        DayCat.FIXED_FEAST,
                        buildString { append("12").append(day) },
                        LitGrade.FERIAE_ADV_II,
                        buildString { append("die").append(day) }
                    )
                }

                array
            }

            // 주님 성탄
            val NATIVITATIS = Core(
                DayCat.FIXED_FEAST,
                "1225",
                LitGrade.NEAP,
                "natdo"
            )

            /**
             * 성탄 팔일 축제 Map
             *
             * 주님 성탄에는 아래와 같이 팔일 축제를 지낸다.
             *
             * ㄱ) 팔일 축제의 주일에 예수, 마리아, 요셉의 성가정 축일을 지낸다.
             *     그러나 팔일 축제 안에 주일이 없으면 12월 30일에 지낸다.
             *
             * ㄴ) 12월 26일에는 성 스테파노 첫 순교자 축일을 지낸다.
             *
             * ㄷ) 12월 27일에는 성 요한 사도 복음사가 축일을 지낸다.
             *
             * ㄹ) 12월 28일에는 죄 없는 아기 순교자들 축일을 지낸다.
             *
             * ㅁ) 12월 29, 30, 31일은 성탄 팔일 축제의 날들이다.
             *
             * ㅂ) 성탄 팔일 축제 1월 1일은 천주의 성모 마리아 대축일을 지낸다.
             *     또한 이날 주님께서 '예수'라는 지극히 거룩하신 이름을 받으신 것을 기억한다.
             *
             * (cf. NUALC, n.35)
             *
             * 다만 성탄 시기를 계산할 때,
             *
             * - 예수, 마리아, 요셉의 성가정 축일
             * - 천주의 성모 마리아 대축일
             *
             * 은 따로 계산하게 되기 때문에 이들은 이 Map에 포함하지 않고,
             * 고유 상수를 만들어 할당하기로 한다.
             */
            val OCT_NAT = {
                val array = mutableMapOf<Int, Core>()

                // 12월 26일 성 스테파노 첫 순교자 축일
                array[26] = Core(
                    DayCat.FIXED_FEAST,
                    "1226",
                    LitGrade.FESTUM_GENERALIS,
                    "stepr"
                )

                // 12월 27일 성 요한 사도 복음사가 축일
                array[27] = Core(
                    DayCat.FIXED_FEAST,
                    "1227",
                    LitGrade.FESTUM_GENERALIS,
                    "ioaap"
                )

                // 12월 28일 죄 없는 아기 순교자들 축일
                array[28] = Core(
                    DayCat.FIXED_FEAST,
                    "1228",
                    LitGrade.FESTUM_GENERALIS,
                    "innma"
                )

                // 12월 29, 30, 31일 성탄 팔일 축제 제5, 6, 7일
                for(day in 29..31) {
                    array[day] = Core(
                        DayCat.FIXED_FEAST,
                        buildString { append("12").append(day) },
                        LitGrade.OCT_NAT,
                        buildString { append("die").append(day) }
                    )
                }

                array
            }

            // 예수, 마리아, 요셉의 성가정 축일
            val FAMILIAE = Core(
                DayCat.MOVEABLE_FEAST,
                LitTemp.NATIVITATIS.toString(),
                LitGrade.FESTUM_DOMINI,
                "famil"
            )

            // 성탄 팔일 축제 제8일, 천주의 성모 마리아 대축일
            val DEI_GENETRICIS = Core(
                DayCat.FIXED_FEAST,
                "0101",
                LitGrade.SOLLEMNITAS_GENERALIS,
                "octna"
            )

            // 주님 공현


            // 주님 세례

            // 성목요일
            // 성금요일
            // 성토요일

            // 주님 승천

        }
    }
}