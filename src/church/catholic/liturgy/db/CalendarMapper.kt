package church.catholic.liturgy.db

import church.catholic.liturgy.day.*
import java.time.LocalDate

class CalendarMapper<Date : Comparable<Date>> {

    private val calendar: HashMap<Date, DayHash> = HashMap()

    fun test() {

        for(i in calendar.keys.sorted()) {
            val text = """
                ${calendar[i]?.litTemp} $i : ${calendar[i]?.sortedItems()}
            """.trimIndent()

            println(text)
        }
    }

    fun getFirstDate(): Date = calendar.keys.minOf { it }

    fun find(date: Date): DayHash = calendar.getValue(date)

    /**
     * `calendar`의 값을 안전하게 추가해 주는 함수
     *
     * 본디 `HashMap.put()`은 동일한 Key에 할당된 Value가 있더라도
     * 그냥 덮어써 버린다.
     *
     * 그러나 전례력 특성상 하나의 날짜에 여러 전례일 항목이 존재할 수 있기 때문에,
     * `HashMap.put()`은 전례력 계산에 곧바로 사용해서는 안 된다.
     *
     * 따라서, `LiturgicalCalendar`의 `calendar` 해시맵 직접 접근을 막고,
     * 안전하게 값을 추가할 수 있는 `CalendarMapper.update()`를 사용하는 것이다.
     */
    fun update(date: Date, dayHash: DayHash) {
        var newDayHash: DayHash = dayHash

        try {
            val oldDayHash = find(date)

            /**
             * 만약 `date`에 이미 할당된 값이 존재한다면,
             * 다음 코드가 실행될 것이다.
             *
             * 그러나 만약 `date`가 처음으로 입력되는 값이어서, `oldDaySet`이 존재하지 않는다면
             * `find()`에서 `NoSuchElementException`을 던질 것이고,
             * `catch` 문으로 넘어가기 때문에
             * 다음 코드는 실행되지 않을 것이다.
             */
            newDayHash = dayHash + oldDayHash

        } catch (t: Throwable) {
            //t.printStackTrace()

        } finally {

            /**
             * `newDaySet`은 위 `try .. catch .. finally`를 통해 적절하게 초기화된다.
             * 만약 `oldDaySet`이 있다면 -> `daySet`과 `oldDaySet`이 합쳐진 것으로,
             * 만약 `oldDaySet`이 없다면 -> 초기 값인 `daySet`만 있는 것으로,
             * 초기화 과정을 거치기 때문에,
             * 마지막에는 `newDaySet`만 새로 할당해 주면 된다.
             */
            calendar[date] = newDayHash
        }
    }

    fun update(date: Date, dayID: DayID, litTemp: LitTemp) {
        this.update(date, DayHash.of(dayID, litTemp = litTemp))
    }

    fun update(date: Date, dayID: DayID?) {

        if(dayID == null) {
            error("dayID is null")
        }

        try {
            val litTemp = find(date).litTemp
            update(date, dayID, litTemp)
        } catch (t: Throwable) {
            require(dayID.cat == DayCat.MOVEABLE_FEAST) {
                "Cannot parse Liturgical Time from dayID : ${dayID.id}"
            }

            this.update(date, DayHash.of(dayID, litTemp = LitTemp.fromCode(dayID.scope)))
        }
    }

    /**
     * `calendar`의 값을 덮어쓰는 함수
     *
     * 덮어쓰기 문제를 해결하기 위해 `CalendarMapper.update()`를 작성했지만,
     * 어떤 경우에는 등급상의 문제로 그냥 날짜가 삭제되는 경우가 있다.
     *
     * 이럴 때 `CalendarMapper.update()`를 사용하면
     * 날짜를 삭제하지 못하고 그저 추가하기 때문에,
     * 나중에 검증 과정이 추가되어야 한다.
     *
     * 이 문제를 해결하기 위해,
     * 기존 `HashMap.put()`의 기능을 그대로 사용할 수 있는
     * `CalendarMapper.override()`를 구현한다.
     */
    fun override(date: Date, dayHash: DayHash) {
        calendar[date] = dayHash
    }

}