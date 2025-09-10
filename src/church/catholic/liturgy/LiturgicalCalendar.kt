package church.catholic.liturgy

import church.catholic.liturgy.day.*
import church.catholic.liturgy.db.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.temporal.TemporalAdjusters

/**
 * 가톨릭 전례력 자바 구현체
 *
 * @param targetYear 전례력을 계산할 기준 연도.
 * ('2025년'인 경우, 2025년 주님 부활을 중심으로
 * 2024년 대림 제1주일부터, 2025년 12월 31일까지 계산된다.)
 *
 */
class LiturgicalCalendar(
    private val targetYear: Year, // '올해' : 전례력을 계산할 기준 연도
    locale: String = "la_VA"
) {

    //var calendarMap: HashMap<LocalDate, DayHash> = HashMap(400)
    private val calendarMapper = CalendarMapper<LocalDate>()
    private val registry = LitDayRegistry(locale)

    // 연도를 매개변수로 전달하기 쉽도록 전환

    private val initOrdinaryI:    LocalDate // '올해' 주님 세례
    private val endOrdinaryI:     LocalDate // 재의 수요일
    private val initOrdinaryII:   LocalDate // 성령 강림
    private val endOrdinaryII:    LocalDate // '올해' 대림 제1주일


    // 생성자에서 계산을 바로 시도한다.
    init {
        val year = targetYear.value

        /*
         * 계산 순서는 다음과 같다.
         * 1. 작년 주님 성탄
         *         가) 작년 대림 시기 (전례력 시작점)
         *         나) 올해 주님 공현, 주님 세례
         *         다) 연초 성탄 시기
         *
         * 2. 주님 부활
         *         가) 재의 수요일, 사순 시기, 성주간, 파스카 성삼일
         *         나) 성령 강림, 주님 승천, 부활 시기
         *
         * 3. 올해 주님 성탄
         *         가) 올해 대림 시기 (전례력 변경점),
         *      나) 올해-내년 성탄 시기
         *
         * 4. 올해 연중 시기
         *         가) 연중 시기 첫째 부분(주님 세례 - 재의 수요일)
         *         나) 연중 시기 둘째 부분(성령 강림 - 대림 시기 직전까지)
         *
         * 5. 보편 전례력 삽입
         */

        // 작년 주님 성탄
        // calculateChristmas()는 targetYear와 매개변수를 비교해서,
        // 매개변수가 '작년'이면 '주님 세례'를 반환한다.
        initOrdinaryI = calculateChristmas(year - 1)

        // 올해 주님 부활
        endOrdinaryI = calculateEaster(year)

        // 성령 강림은 재의 수요일 95일 뒤이다.
        initOrdinaryII = endOrdinaryI.plusDays(95)

        // 올해 주님 성탄
        endOrdinaryII = calculateChristmas(year)

        // 올해 연중 시기 연결
        calculateOrdinaryTime()

        // 보편 전례력을 덮어쓴다.
        overrideRomanCalendar(year)

        // 전례 등급의 우선 순위에 따라 날짜를 조정, 삭제한다.
        organizeCalendar()
    }

    private fun calculateChristmas(year: Int): LocalDate{
        // 1. 주님 성탄
        val christmas: LocalDate = LocalDate.of(year, 12, 25)

        calendarMapper.update(
            christmas,
            registry.find(LitDayRegistry.Core.NATIVITATIS),
            LitTemp.NATIVITATIS
        )

        /*
         * 1-가. 대림 시기
         *
         * 성탄 직전 주일은 대림 제4주일이고, 그 3주 전은 대림 제1주일이다.
         * 성탄이 주일인 경우, 그 전 주일이 대림 제4주일이 된다.
         *
         * LocalDate의 DayOfWeek는 월요일부터 1씩 세고, 주일은 7이다.
         * 따라서 christmas의 DayOfWeek 상수를 날에서 빼주면
         * '직전 주일'이 나오게 된다.
         */
        val da4 = christmas.minusDays(christmas.dayOfWeek.value.toLong())
        calendarMapper.update(
            da4,
            registry.findSunday(LitTemp.ADVENTUS, 4)
        )

        /*
         * 대림 제4주일에서 1주일씩 빼서 대림 제3, 2, 1주일을 구한다.
         * LocalDate da4는 불변하는 인스턴스(Immutable Instance)이기 때문에,
         * da4를 기준으로 계산한다.
         */
        for (i in 1..3) {
            calendarMapper.update(
                da4.minusWeeks(4L - i),
                registry.findSunday(LitTemp.ADVENTUS, i)
            )
        }

        /*
         * 대림 시기 평일
         *
         * 대림 시기에는 첫째 부분과 둘째 부분이 있다.
         * 첫째 부분은 대림 제1주일부터 12월 16일까지로,
         * 평일은 '대림 제n주간 n요일'로 표시한다. (주일은 '대림 제n주일')
         *
         * 둘째 부분은 12월 17일부터 24일까지로,
         * 평일은 '12월 n일'로 표시한다. (주일은 '대림 제n주일')
         *
         * 둘째 부분이 있기 때문에,
         * 첫째 부분은 대림 제3주간 금요일까지만 있다.
         *
         * 계산은 간단하게 하기 위해서,
         * 대림 제3주간 금요일까지 일단 삽입한 다음,
         * 12월 17일부터 덮어쓰도록 한다.
         */
        for (week in 1L..3L) {

            val d: LocalDate = da4.minusWeeks(4L - week)

            for (dow in 1L..6L) {
                // 대림 제3주간 토요일은 없기 때문에 건너뛴다.
                if (week == 3L && dow == 6L) {
                    break // week 3 dow 6은 마지막 루프이다.
                }

                /*
                 * - d는 불변 객체이므로 기준으로 삼는다.
                 */
                val wd = d.plusDays(dow)

                calendarMapper.update(
                    wd,
                    registry.findWeekdays(
                        LitTemp.ADVENTUS,
                        week.toInt()
                    )[wd.dayOfWeek]
                )
            }
        }

        for (day in 17..24) {
            // 매 루프 초기화된다.
            val d: LocalDate = LocalDate.of(year, 12, day)

            // 주일은 건너뛴다.
            if (d.getDayOfWeek() === DayOfWeek.SUNDAY) {
                continue  // 12월 17일 - 24일 사이에 주일이 오는 경우가 있으므로
            }

            calendarMapper.override(
                d,
                DayHash.of(
                    registry.find(LitDayRegistry.Core.FERIAE_ADV_II()[day]),
                    litTemp = LitTemp.ADVENTUS
                )
            )
        }

        /*
         * 연말 성탄 시기
         *
         * 성탄 시기는 주님 성탄 대축일 제1 저녁 기도부터 시작하여
         * 주님 공현 대축일 곧 1월 6일 다음 주일까지 계속된다.
         * (cf. NUALC, n.33)
         * 1월 6일 다음 주일에는 주님 세례 축일을 지낸다.
         * (cf. NUALC, n.6 §2)
         *
         * 이상에서, 주님 성탄 대축일 제1 저녁 기도부터 주님 세례 축일까지를
         * 성탄 시기라고 생각할 수 있다.
         *
         * 다만 이 함수의 특성상, return value 결정에서 '내년'은 포함할 수 없기 때문에,
         * 여기서는 12월 26일부터 31일까지 연말 성탄 시기만 먼저 입력하도록 한다.
         * 입력 연도 판별 이후 계산 내용과 반환값이 결정될 것이다.
         *
         * 연말의 성탄 시기 계산에서 주의할 점은 다음과 같다.
         *         - 성탄 팔일 축제. (cf. NUALC, n.35) 날짜가 고정되어 있다.
         *         - 팔일 축제의 주일에 예수, 마리아, 요셉의 성가정 축일을 지낸다.
         *           그러나 팔일 축제 안에 주일이 없으면 12월 30일에 지낸다.
         *           (cf. NUALC, n.35 §1)
         *
         */

        // 성가정 축일, 천주의 성모 마리아 대축일이 포함되지 않은 전례일을 입력한다.
        for(day in 26..31) {
            // 매 루프 초기화된다.
            val d: LocalDate = LocalDate.of(year, 12, day)

            calendarMapper.update(
                d,
                registry.find(LitDayRegistry.Core.OCT_NAT()[day]),
                litTemp = LitTemp.NATIVITATIS
            )
        }

        /*
         * 예수, 마리아, 요셉의 성가정 축일
         *
         * 팔일 축제의 주일에 예수, 마리아, 요셉의 성가정 축일을 지낸다.
         * 그러나 팔일 축제 안에 주일이 없으면 12월 30일에 지낸다.
         * (cf. NUALC, n.35 §1)
         *
         * 주님 성탄이 주일인 때, 팔일 축제 안에 주일이 없게 된다.
         */
        val familiae = registry.find(LitDayRegistry.Core.FAMILIAE)
        if (christmas.getDayOfWeek() === DayOfWeek.SUNDAY) {
            calendarMapper.update(
                LocalDate.of(year, 12, 30),
                familiae
            )
        } else {
            for (i in 1L..7L) {
                val d: LocalDate = christmas.plusDays(i)
                if (d.getDayOfWeek() === DayOfWeek.SUNDAY) {
                    calendarMapper.update(
                        d,
                        familiae
                    )
                }
            }
        }

        /**
         * return value
         *
         * 이 함수는 입력된 연도의 주님 성탄과,
         * 주님 성탄 앞의 대림 시기,
         * 주님 성탄 뒤의 성탄 시기(당해연말-내년초)를 계산한다.
         *
         * 대단히 간략하게 말하자면, 올해 대림 시기와 내년 성탄 시기를 계산한다.
         *
         * 따라서 전체 전례력을 계산하기 위해서는,
         * 이 함수는 '두 번' 실행되어야 한다.
         *
         * 첫째 실행에서, 매개 변수 `year`는 '작년'이고,
         * 작년 대림 제1주일부터 올해 주님 세례 축일까지를 계산한다.
         *
         * 둘째 실행에서, 매개 변수 `year`는 '올해'이고,
         * 올해 대림 제1주일부터 12월 31일까지를 계산한다.
         * (내년 연초 성탄 시기는 필요 없기 때문에 건너뛰도록 한다)
         *
         * '올해 주님 세례 축일'과 '올해 대림 제1주일'이
         * 연중 시기 계산을 위해 필요하다는 것을 고려하면,
         *
         * 첫째 실행(작년 계산)에서, 이 함수는 '주님 세례'를 반환하고,
         * 둘째 실행(올해 계산)에서, 이 함수는 '대림 제1주일'을 반환해야 한다.
         */
        if(this.targetYear.value == year) {

            // 올해를 계산하고 있으면, 다음 해 부분의 계산은 건너뛰고
            // 대림 제1주일을 반환한다.
            return da4.minusWeeks(3)

        } else {

            // '작년'을 계산하고 있으면 '다음 해' 곧 올해분을 계산하고 주님 세례를 반환한다.

            // 1-나. 다음 해 주님 공현, 주님 세례

            /**
             * 주님 공현
             *
             * 주님 공현 대축일이 1월 6일이지만,
             * 의무 축일이 아닌 곳에서는 1월 2일과 8일 사이에 오는 주일에 지낸다.
             * *(cf. NUALC, nn.37 et 7 §1.)*
             *
             * 주님 공현 의무 여부에 따라 주님 공현을 계산한다.
             */
            val epiphania: LocalDate = if(true /*주님 공현이 의무인 곳*/) {
                // 주님 공현이 의무인 곳에서는 1월 6일이 주님 공현이다.
                LocalDate.of(year + 1, 1, 6)
            } else {
                // 주님 공현이 의무 아닌 곳에서는 1월 2일과 8일 사이에 오는 주일이 주님 공현이다.
                // 계산의 편의를 위해, 1월 1일 다음의 주일을 찾도록 한다.
                // 1월 1일 다음의 주일은 반드시 1월 2일과 8일 사이에 오기 때문이다.
                LocalDate.of(year + 1, 1, 1).with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
            }

            /**
             * 주님 세례
             *
             * 1월 6일 다음 주일에는 주님 세례 축일을 지낸다.
             * *(cf. NUALC, n.6 §2)*
             * 공현 대축일을 1월 7일이나 8일에 오는 주일로 옮겨 지내는 곳에서는,
             * 주님 세례 축일은 바로 다음 월요일에 지낸다.
             * *(cf. 로마 보편 전례력, 일월.)*
             *
             * 주님 세례의 계산은,
             * - 주님 공현이 1월 7일 전이면 : 주님 공현 다음 주일.
             * - 주님 공현이 1월 7일 또는 8일이라면, 주님 공현 바로 다음 월요일.
             * 로 계산하도록 한다.
             *
             * `LocalDate.isBefore()`은 '당일'을 포함하지 않기 때문에,
             * 1월 6일 '까지의' 날을 확인하려면, 1월 7일 '전'인지 확인해야 한다.
             */
            val baptismate: LocalDate = if(epiphania.isBefore(LocalDate.of(year + 1, 1, 7))) {
                // 주님 공현이 1월 7일 전인 경우 주님 공현 다음 주일이 주님 세례이다.
                epiphania.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
            } else {
                // 주님 공현이 1월 7일 또는 8일인 경우, 주님 공현 바로 다음 월요일이 주님 세례이다.
                epiphania.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            }

            calendarMapper.update(
                epiphania,
                registry.find("mov.nati.210.epiph")
            )
            calendarMapper.update(
                baptismate,
                registry.find("mov.nati.510.bapti")
            )

            /*
             * 1-다. 연초 성탄 시기
             *
             * 위에서는 연말 성탄 시기를 계산했다면, 여기서는 연초 성탄 시기를 계산한다.
             * '주님 성탄'을 중심으로 전개하는 특성상,
             * 연초의 성탄 시기를 계산하기 위해서는 작년 주님 성탄과 연계하여야 한다.
             * 따라서 '작년'을 계산하고 있음이 확실한 이 부분 플로우에서
             * 연초 성탄 시기를 계산한다.
             *
             * 연초의 성탄 시기 계산에 주의할 점은 다음과 같다.
             *         - 1월 1일은 천주의 성모 마리아 대축일을 지낸다.
             *           (cf. NUALC, n.35 §6)
             *         - 1월 2일과 5일 사이에 오는 주일은 성탄 후 제2주일이다.
             *           (cf. NUALC, n.36)
             *         - 주님 공현 전에 오는 평일은 '주님 공현 전 n요일',
             *           주님 공현 후에 오는 평일은 '주님 공현 후 n요일'이라고 한다.
             *           전례문은 공통된 부분도 있고, 다른 부분도 있다.
             */

            // 천주의 성모 마리아 대축일
            calendarMapper.update(
                LocalDate.of(year + 1, 1, 1),
                registry.find(LitDayRegistry.Core.DEI_GENETRICIS),
                litTemp = LitTemp.NATIVITATIS
            )

            /*
             * 성탄 후 제2주일
             *
             * 주님 공현이 의무 아닌 곳에서는
             * 1월 2일과 8일 사이에 오는 주일이 이미 주님 공현 대축일이고,
             * 그 다음 주일은 주님 세례 축일 또는 연중 제2주일이기 때문에,
             * 성탄 후 제2주일을 가질 수 없다.
             *
             * 곧, 주님 공현이 의무인 곳이어서 주님 공현을 1월 6일에 지내는 곳에서만
             * 1월 2일과 5일 사이에 오는 주일에 성탄 후 제2주일을 가진다.
             * 다만 1월 2일부터 5일까지 전부 평일인 경우에는 성탄 후 제2주일이 없다.
             */

            // 주님 공현이 의무인지 확인한다.
            if (true /*주님 공현이 의무인 곳*/) {
                for (day in 2..5) {
                    val d: LocalDate = LocalDate.of(year + 1, 1, day)
                    if (d.getDayOfWeek() === DayOfWeek.SUNDAY) {
                        calendarMapper.update(
                            d,
                            registry.findSunday(LitTemp.NATIVITATIS, 2)
                        )
                    }
                }
            }

            /*
             * 성탄 시기 평일
             *
             * 성탄 시기 평일은 1월 2일부터 주님 세례 축일 전 토요일까지이다.
             *
             * 주님 공현 전에 오는 평일은 '주님 공현 전 n요일',
             * 주님 공현 후에 오는 평일은 '주님 공현 후 n요일'이라고 한다.
             * 전례문은 공통된 부분도 있고, 다른 부분도 있다.
             *
             * 계산의 편의를 위해,
             * 1월 2일부터 주님 세례 축일 전날까지 날짜를 따라가며 주일을 건너뛴다.
             */
            for (day in 2..<baptismate.dayOfMonth) {
                val d: LocalDate = LocalDate.of(year + 1, 1, day)

                // 주일과 주님 공현을 건너뛴다.
                // 주님 공현이 의무인 곳에서는 주님 공현이 평일일 수 있기 때문이다.
                if (d.getDayOfWeek() === DayOfWeek.SUNDAY) continue
                if (d.isEqual(epiphania)) continue

                calendarMapper.update(
                    d,
                    registry.findWeekdays(
                        LitTemp.NATIVITATIS,
                        if(d.isBefore(epiphania)) {
                            1 // 주님 공현 전 주간을 DB에서는 성탄 제1주간으로 간주
                        } else {
                            2 // 주님 공현 후 주간을 DB에서는 성탄 제2주간으로 간주
                        }
                    )[d.dayOfWeek]
                )
            }

            // 작년을 계산하고 있었기 때문에, 주님 세례를 반환한다.
            return baptismate
        }
    }

    private fun calculateEaster(year: Int): LocalDate{
        // 2. 주님 부활
        val easter: LocalDate = getEasterDate(year)

        calendarMapper.update(
            easter,
            registry.findSunday(
                LitTemp.PASCHALIS,
                1
            )
        )

        /*
         * 2-가. 재의 수요일, 사순 시기, 성주간, 파스카 성삼일
         *
         * 주님 부활 대축일에서 역순으로 되돌아가면 모두 계산할 수 있다.
         */

        // 파스카 성삼일 : 주님 부활 1, 2, 3일 전(성주간 목요일은 따로 처리)
        for (i in 1L..3L) {
            calendarMapper.update(
                easter.minusDays(i),
                registry.find(
                    DayCat.MOVEABLE_FEAST,
                    LitTemp.QUADRAGESIMAE.toString(),
                    LitGrade.TRIDUUM_PASCHALE,
                    when(i) {
                        1L -> "sabsa"   // 1일 전 : 성토요일
                        2L -> "pasdo"   // 2일 전 : 성금요일
                        3L -> "cendo"   // 3일 전 : 성목요일
                        else -> ""      // 그럴 일이 없음
                    }
                )
            )
        }


        // 성주간 : 주님 부활 7, 6, 5, 4, 3일 전(성주간 목요일은 여기서 처리)
        for (i in 3L..7L) {
            val d = easter.minusDays(i)

            if (i == 7L) {
                calendarMapper.update(
                    d,
                    registry.findSunday(
                        LitTemp.QUADRAGESIMAE,
                        6   // 주님 수난 성지 주일은 DB에서 사순 제6주일로 간주
                    )
                )
                break
            }

            calendarMapper.update(
                d,
                registry.findWeekdays(
                    LitTemp.QUADRAGESIMAE,
                    6   // 성주간은 DB에서 사순 제6주간으로 간주 (다만 목요일까지만 있음)
                )[d.dayOfWeek]
            )
        }

        /*
         * 사순 시기의 주일과 평일
         *
         * 이 시기의 주일은 사순 제1, 2, 3, 4, 5주일이라 부른다.
         * 성주간이 시작되는 사순 제6주일은 '주님 수난 성지 주일'이라 한다.
         * (cf. NUALC, n.30)
         *
         * 계산에 있어서,
         * 주님 수난 성지 주일은 성주간과 함께 처리했기 때문에 제외한다.
         * 사순 제6주간 평일은 성주간 그 자체이기 때문에 제외한다.
         */
        for (week in 1L..5L) {
            val d: LocalDate = easter.minusWeeks(7 - week)
            calendarMapper.update(
                d,
                registry.findSunday(
                    LitTemp.QUADRAGESIMAE,
                    week.toInt()
                )
            )

            for (dow in 1L..6L) {
                val wd = d.plusDays(dow)
                calendarMapper.update(
                    wd,
                    registry.findWeekdays(
                        LitTemp.QUADRAGESIMAE,
                        week.toInt()
                    )[wd.dayOfWeek]
                )
            }
        }


        /*
         * 재의 수요일
         *
         * 재의 수요일의 날짜를 규정하는 명문 규정은 없다.
         * 다만, 언제나 사순 제1주일 전 수요일이고,
         * 날짜로 계산하면 주님 부활 46일 전이다.
         *
         * 재의 수요일에 이어지는 평일은 '재의 예식 다음 n요일'로 불린다.
         */
        val cinerum: LocalDate = easter.minusDays(46)

        for (i in 3..6) {
            val d = cinerum.with(DayOfWeek.of(i))

            calendarMapper.update(
                d,
                registry.findWeekdays(
                    LitTemp.QUADRAGESIMAE,
                    0   // 재의 수요일과 재의 수요일 다음 평일은 DB에서 사순 제0주일로 간주한다. (다만 수요일부터 있음)
                )[d.dayOfWeek]
            )
        }


        /*
         * 2-나. 성령 강림, 주님 승천, 부활 시기
         *
         * 주님 부활 대축일 다음 주일들을
         * 부활 제2, 3, 4, 5, 6, 7주일이라 부른다.
         * 이 거룩한 50일 동안 지내는 부활 시기는
         * 성령 강림 대축일로 끝난다.
         * (cf. NUALC, n.23)
         *
         * 부활 시기를 시작하는 팔일은 부활 팔일 축제를 이루며
         * 주님의 대축일로 지낸다.
         * (cf. NUALC, n.24)
         *
         * 주님 부활 대축일 다음 40일에는 주님의 승천을 경축한다.
         * 이날을 의무 축일로 지내지 않는 지역에서는
         * 부활 제7주일이 주님 승천 대축일로 지정된다.
         * (cf. NUALC, n.25. 7 §2)
         *
         * 계산은 단순히 날짜를 더하기만 하면 된다.
         */

        // 부활 팔일 축제를 계산한다.
        for (i in 1L..6L) {
            val d = easter.plusDays(i)
            calendarMapper.update(
                d,
                registry.findWeekdays(
                    LitTemp.PASCHALIS,
                    1   // 부활 팔일 축제는 DB에서 부활 제1주간으로 간주한다
                )[d.dayOfWeek]
            )
        }


        /*
         * 주님 승천
         *
         * 주님 승천이 의무인 곳에서는
         * 주님 부활 대축일 다음 40일, 곧 부활 제6주간 목요일에,
         *
         * 의무 아닌 곳에서는 부활 제7주일에 거행한다.
         */

        // 부활 시기를 계산한다.
        for (week in 2L..7L) {
            val d: LocalDate = easter.plusWeeks(week - 1)

            calendarMapper.update(
                d,
                registry.findSunday(
                    LitTemp.PASCHALIS,
                    week.toInt()
                )
            )

            if (false /*주님 승천이 의무 아닌 곳*/) {
                if (week == 7L) { // 부활 제7주일에
                    calendarMapper.override(
                        d,
                        DayHash.of(
                            registry.find("mov.psch.210.ascen"),
                            litTemp = LitTemp.PASCHALIS
                        )
                    )
                }
            }

            for (dow in 1L..6L) {
                val wd = d.plusDays(dow)
                calendarMapper.update(
                    wd,
                    registry.findWeekdays(
                        LitTemp.PASCHALIS,
                        week.toInt()
                    )[wd.dayOfWeek]
                )
                if (true /*주님 승천이 의무인 곳*/) {
                    if (week == 6L && dow == 4L) { // 부활 제6주간 목요일에
                        calendarMapper.override(
                            wd,
                            DayHash.of(
                                registry.find("mov.psch.210.ascen"),
                                litTemp = LitTemp.PASCHALIS
                            )
                        )
                    }
                }
            }
        }

        // 성령 강림 : 부활 제8주일
        val pentecostes: LocalDate = easter.plusWeeks(7)
        calendarMapper.update(
            pentecostes,
            registry.findSunday(
                LitTemp.PASCHALIS,
                8
            )
        )

        return cinerum
    }

    /**
     * 올해 연중 시기를 계산하는 함수
     *
     * 반드시 미리 `calculateChristmas()`,
     * `calculateEaster()`를 호출한 다음에 사용해야 한다.
     *
     * 연중 시기는 1월 6일 다음 주일에 뒤따르는 월요일에 시작하여
     * 사순 시기 전 화요일까지 계속된다.
     * 그리고 성령 강림 대축일 다음 월요일에 다시 시작하여
     * 대림 제1주일의 제1 저녁 기도 직전에 끝난다.
     * *(cf. NUALC, n.44)*
     *
     * 계산을 위해서,
     * 연중 시기 첫째 부분은 주님 세례 축일 다음 날부터
     * 재의 수요일 전날까지,
     *
     * 연중 시기 둘째 부분은 성령 강림 대축일 다음 날부터
     * 대림 제1주일 전날까지로 계산한다.
     */
    private fun calculateOrdinaryTime() {

        // 연중 시기 첫째 부분
        var ordinary2: LocalDate = initOrdinaryI.plusWeeks(1)


        // 연중 제1주간을 삽입한다.
        for (dow in 1L..6L) {
            val d: LocalDate = initOrdinaryI.plusDays(dow)
            if (d.getDayOfWeek() === DayOfWeek.SUNDAY) {
                ordinary2 = d
                break
            }
            calendarMapper.update(
                d,
                registry.findWeekdays(
                    LitTemp.PER_ANNUM,
                    1
                )[d.dayOfWeek]
            )
        }


        // 연중 시기 첫째 부분 마지막 주
        var ordinaryWeekI: Long?

        var week = 2L
        weekLoop@ while (true) {
            val d: LocalDate = ordinary2.plusWeeks(week - 2)
            calendarMapper.update(
                d,
                registry.findSunday(
                    LitTemp.PER_ANNUM,
                    week.toInt()
                )
            )

            for (dow in 1L..6L) {
                val df: LocalDate = d.plusDays(dow)
                if (df.isEqual(endOrdinaryI)) {
                    ordinaryWeekI = week
                    break@weekLoop
                }
                calendarMapper.update(
                    df,
                    registry.findWeekdays(
                        LitTemp.PER_ANNUM,
                        week.toInt()
                    )[df.dayOfWeek]
                )
            }
            week++
        }

        // 연중 시기 둘째 부분

        // 대림 제1주일 전 주는 연중 제34주간이다.
        val ordinary34: LocalDate = endOrdinaryII.minusWeeks(1)

        for (week in 34 downTo ordinaryWeekI + 1) {
            val d: LocalDate = ordinary34.minusWeeks(34 - week)

            for (dow in 1L..6L) {
                val wd = d.plusDays(dow)
                calendarMapper.update(
                    wd,
                    registry.findWeekdays(
                        LitTemp.PER_ANNUM,
                        week.toInt()
                    )[wd.dayOfWeek]
                )
            }

            if (!d.isEqual(initOrdinaryII)) {

                // 그리스도왕 대축일은 DB에서 연중 제34주일로 간주된다.
                calendarMapper.update(
                    d,
                    registry.findSunday(
                        LitTemp.PER_ANNUM,
                        week.toInt()
                    )
                )
            } else {
                break
            }
        }
    }

    private fun overrideRomanCalendar(year: Int) {
        // 작년 분을 덮어쓴다.

        val firstDay: LocalDate = calendarMapper.getFirstDate()

        generateSequence(firstDay) { it.plusDays(1) }
            .takeWhile { it.isBefore(LocalDate.of(year + 1, 1, 1)) }
            .forEach { date ->
                val dbDayHash = registry.findDayHash(date)

                if(dbDayHash != null) {
                    calendarMapper.update(date, dbDayHash)
                }
            }
    }

    /**
     * 전례일 등급과 순위에 따른 전례일 조정 함수
     *
     * 같은 날 여러 전례 거행이 겹치면 '전례일의 등급과 순위 표'에 따라
     * 등급이 더 높은 축제를 지낸다.
     * 대축일을 순위가 더 높은 다른 전례일 때문에 지낼 수 없다면,
     * 이 규범 5항의 규정을 지키며,
     * '전례일의 등급과 순위 표' 1-8항에 해당되지 않는 가까운 날로 옮겨 지낸다.
     * 그러나 주님 탄생 예고 대축일이 성주간 어떤 날에 올 때에는 언제나 부활 제2주일 다음 월요일로 옮겨 지낸다.
     *
     * 다른 전례 거행들은 그때에는 없어진다.
     * (cf. NUALC, n.60)
     *
     * 주일은 이렇게 중요하므로 대축일과 주님의 축일에만 자리를 내준다.
     * 그러나 대림 사순 부활 시기의 주일은 모든 주님의 축일과 모든 대축일보다 앞선다.
     * 주님 수난 성지 주일이나 주님 부활 대축일이 아닌 이런 주일에 오는 대축일들은
     * 뒤따르는 월요일로 옮겨 지낸다.
     * (cf. NUALC, n.5)
     *
     */
    private fun organizeCalendar() {
        val firstDay = calendarMapper.getFirstDate()

        generateSequence(firstDay) { it.plusDays(1) }
            .takeWhile { it.isBefore(LocalDate.of(targetYear.value + 1, 1, 1)) }
            .forEach { date ->
                val originalHash = calendarMapper.find(date)
                val idSet = originalHash.sortedItems()
                val organizedSet = mutableSetOf<DayID>()
                if(idSet.size > 1) {
                   organizedSet.add(idSet[0]) // 가장 순위가 높은 하나는 반드시 넣어 준다.

                    when(idSet[0].grade) {
                        // 파스카 성삼일에는 오직 '성목요일'만 겹칠 수 있다.
                        LitGrade.TRIDUUM_PASCHALE -> {
                            if(idSet[1].grade == LitGrade.FERIA_V_HEBD_SANC) {
                                organizedSet.add(idSet[1])
                            }
                        }
                        LitGrade.FERIA_V_HEBD_SANC -> {
                            error("주님 만찬 성목요일 어디감?")
                        }
                        LitGrade.NEAP -> {
                            // 주님 성탄, 공현, 승천, 성령 강림에 무엇이 겹쳤으면 무시한다.
                        }
                        LitGrade.DOMINICA_ADV_QUAD_PASCH -> {
                            // 대림 사순 부활 시기의 주일에 무엇이 겹쳤다면,
                            // 그 다음에 무엇이 오는가에 따라 검증된다.
                            for(i in 1..<idSet.size) {
                                if(idSet[i].grade < LitGrade.PRO_VARIIS_NECESSITATIBUS_I) {
                                    // 기원 1보다 높은 것은 대축일급 이상이다.
                                    calendarMapper.update(
                                            date.plusDays(1), // 주일 바로 다음날인 월요일로 이동한다.
                                            idSet[i]
                                        )
                                }
                            }
                            // 대축일급 미만은 무시한다.
                        }
                        //LitGrade.PRO_DEFUNCTIS_I -> TODO()
                        LitGrade.FERIA_IV_CINERUM -> {
                            // 재의 수요일은 부활 46일 전이기 때문에, 2월 4일부터 3월 10일까지이다.
                            // 이 기간에 대축일은 없기 때문에, 나머지는 모두 무시한다.
                        }
                        LitGrade.FERIAE_HEBD_SANCTAE -> {

                            if(idSet[1].grade < LitGrade.PRO_VARIIS_NECESSITATIBUS_I) {
                                // 성주간에 대축일이 오는 경우는 단 두 경우가 있다.
                                // 1. 3월 19일, 복되신 동정 마리아의 배필 성 요셉 대축일
                                // 2. 3월 25일, 주님 탄생 예고 대축일.

                                // 성 요셉 대축일의 경우, 주님 수난 성지 주일 이전 토요일로 옮긴다.
                                if(date.isEqual(LocalDate.of(targetYear.value, 3, 19))) {
                                    calendarMapper.override( // 반복을 회귀할 수 없기 때문에 불가피하게 override를 한다.
                                        date.with(TemporalAdjusters.previous(DayOfWeek.SATURDAY)),
                                        DayHash(setOf(idSet[1]), litTemp = LitTemp.QUADRAGESIMAE)
                                    )
                                }

                                // 주님 탄생 예고 대축일의 경우, 부활 제2주일 다음 월요일로 옮긴다.
                                if(date.isEqual(LocalDate.of(targetYear.value, 3, 25))) {
                                    calendarMapper.update(
                                        date.plusWeeks(2).with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
                                        DayHash(setOf(idSet[1]), litTemp = LitTemp.PASCHALIS)
                                    )
                                }

                                // 이외에는 무시한다.
                            }
                        }
                        LitGrade.OCT_PASCH -> {
                            // 부활 팔일 축제에 대축일이 겹치는 경우는 단 하나, 주님 탄생 예고 대축일이다.

                            // 그 대축일을 거행할 수 있는 가장 가까운 날은 부활 제2주일 다음 월요일이다.
                            if(date.isEqual(LocalDate.of(targetYear.value, 3, 25))) {
                                calendarMapper.update(
                                    date.with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
                                    DayHash(setOf(idSet[1]), litTemp = LitTemp.PASCHALIS)
                                )
                            }

                            // 이외에는 무시한다.
                        }

                        //LitGrade.SOLLEMNITAS_GENERALIS -> TODO()
                        //LitGrade.OMNIUM_FIDELIUM_DEFUNCTORUM -> TODO()
                        //LitGrade.SOLLEMNITAS_PATRONI -> TODO()
                        //LitGrade.SOLLEMNITAS_DEDICATIONIS -> TODO()
                        //LitGrade.SOLLEMNITAS_TITULI -> TODO()
                        //LitGrade.SOLLEMNITAS_TITULI_ORDINIS -> TODO()
                        //LitGrade.PRO_VARIIS_NECESSITATIBUS_I -> TODO()
                        //LitGrade.FESTUM_DOMINI -> TODO()
                        //LitGrade.DOMINICA_NAT_ANNUM -> TODO()
                        //LitGrade.FESTUM_GENERALIS -> TODO()
                        //LitGrade.FESTUM_PATRONI_DIOECESIS -> TODO()
                        //LitGrade.FESTUM_DEDICATIONIS -> TODO()
                        //LitGrade.FESTUM_PATRONI_REGIONIS -> TODO()
                        //LitGrade.FESTUM_TITULI_ORDINIS -> TODO()
                        //LitGrade.ALIUM_FESTUM_ECCLESIAE -> TODO()
                        //LitGrade.ALIUM_FESTUM_DIOECESIS -> TODO()
                        //LitGrade.PRO_DEFUNCTIS_II -> TODO()
                        LitGrade.FERIAE_ADV_II,
                        LitGrade.OCT_NAT,
                        LitGrade.FERIAE_QUAD -> {
                            for(i in 1..<idSet.size) {
                                if(idSet[i].grade <= LitGrade.MEMORIA_AD_LIBITUM) {
                                    organizedSet.add(idSet[i])
                                }
                            }
                        }
                        //LitGrade.PRO_VARIIS_NECESSITATIBUS_II -> TODO()
                        //LitGrade.MEMORIA_OBLIGATORIA -> TODO()
                        //LitGrade.MEMORIA_PATRONI -> TODO()
                        //LitGrade.MEMORIA_PROPRIA -> TODO()
                        //LitGrade.MEMORIA_AD_LIBITUM -> TODO()
                        LitGrade.FERIAE_ADV_I,
                        LitGrade.FERIAE_NAT,
                        LitGrade.FERIAE_PASCH,
                        //LitGrade.PRO_DEFUNCTIS_III -> TODO()
                        //LitGrade.PRO_VARIIS_NECESSITATIBUS_III -> TODO()
                        LitGrade.FERIAE_PER_ANNUM -> {
                            for(i in 1..<idSet.size) {
                                organizedSet.add(idSet[i])
                            }
                        }
                        else -> { }
                    }

                } else {
                    organizedSet.addAll(idSet)
                }
                calendarMapper.override(
                    date,
                    DayHash(
                        organizedSet,
                        litTemp = originalHash.litTemp
                    )
                )
            }
    }

    /**
     * 주님 부활의 날짜 계산
     *
     * 주님 부활의 날짜는 흔히 '춘분 후 만월 다음 주일'로 알려져 있다.
     *
     * 주님 수난과 부활의 파스카 축제는 유다 달력에서 니산 달 14일이었다.
     * 그래서 초기 그리스도교 공동체는 유다 달력의 니산 달 14일을
     * 요일에 상관하지 않고 주님 부활로 지냈다.
     *
     * 그러나 점점 초기 그리스도교 공동체 가운데에서
     * 주님 부활을 '주일'에 거행하려는 관습이나,
     * 유다인들의 윤년 계산에 의문을 품고, '그리스도교식' 니산 달을
     * '춘분' 뒤로 오도록 계산하여 주님 부활을 거행하려는 관습 등이 생겨났다.
     * 이러한 관습들이 난립하면서 지역마다 주님 부활을 다른 날에 거행하는 문제가 생겼다.
     *
     * 이에 325년 제1차 니케아 보편 공의회는 주님 부활의 계산에 있어서,
     * - 음력을 사용하는 유다 달력에서 독립할 것,
     * - 전 세계가 로마와 알렉산드리아 교회가 주님 부활 대축일을 거행하는 날과
     * 동일한 날에 주님 부활 대축일을 거행할 것을 결정했다.
     * 이 결정에 힘입어 교회는 당시 로마 달력이었던 율리우스력을 사용해,
     * 춘분을 3월 21일로 고정하고 춘분과 망일(보름)을 기준으로
     * 주님 부활을 계산하기 시작했다.
     *
     * 그러나 율리우스력은 365.2422년의 실제 태양 공전 주기를 정확히 반영하지 못했고,
     * 오차가 누적되어 1582년에 이르러서는 실제 천문학적 춘분이 3월 11일에 오는 등
     * 날짜가 정확하지 못한 문제가 있었다.
     *
     * 이에 1582년 그레고리오 13세 교황은
     * 그레고리력을 설정하는 교령 Inter Gravissimas을 선포하여,
     * - 1582년 10월 5일 다음날을 10월 15일로 하여 오차를 삭제하고
     * 춘분 날짜를 3월 21일로 복원하였으며,
     * - 새로운 역법인 그레고리력을 설정하여 오차를 보정하였다.
     *
     * 위 교령에서 그레고리오 교황은 부활절 계산의 요소로 3가지를 언급하는데,
     * - 정확한 춘분의 일자,
     * - 정월[그리스도교식 니산 달, 곧 음력] 14일,
     * - 정월 14일의 바로 다음 주일이다.
     *
     * 우리가 흔히 말하는 춘분 후 만월 다음 주일은 이 교령에서 비롯되었다.
     * 교령에서 14일이라고 하였지만 흔히 '만월' 곧 음력 15일을 언급하는 이유는,
     * 교령이 언급하는 음력 14일은 춘분 당일일 수 있기 때문이기도 하고,
     * '만월'이 더 도드라진 날로서 기억하기 쉽기 때문일 것이다.
     */

    /**
     * 주님 부활의 날짜를 계산하는 함수
     *
     *
     * 주님 부활의 날짜는 흔히 '춘분 후 만월 다음 주일'로 알려져 있지만,
     * 이를 실제로 천문학적으로 계산하려면 태양과 달의 궤도를 모두 계산해야 한다.
     *
     *
     * 다만, 존경하올 수학자들이 오랜 시간을 거쳐 여러 방법으로
     * 사칙 연산을 통해 주님 부활의 날짜를 계산하는 알고리즘을 제안해 왔다.
     *
     *
     * 본인은 이러한 수학적 성과를 기꺼이 받아들여 주님 부활의 날짜를 계산한다.
     * 특히 이 함수에서는 수정된 익명 그레고리안 함수를 적용한다.
     *
     *
     *
     * Originated from: Anonymous (20 April 1876). "To find Easter". *Nature*: 487. <br></br>
     * Modified by: O'Beirne, T.H. (30 March 1961). "How ten divisions lead to Easter".
     * *New Scientist*. 9 (228): 828.
     *
     *
     * @param year 주님 부활의 날짜를 구하고자 하는 연도.
     * @return 주님 부활의 날짜를 담은 `LocalDate` 객체.
     */
    private fun getEasterDate(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val g = (8 * b + 13) / 25
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 19 * l) / 433
        val n = (h + l - 7 * m + 90) / 25 // the month of the Easter day.
        val p = (h + l - 7 * m + 33 * n + 19) % 32 // the date of the Easter day.

        return LocalDate.of(year, n, p)
    }

//    fun test() {
//        calendarMapper.test()
//    }
}

