package church.catholic.liturgy.db

import church.catholic.liturgy.hash.DaySet
import java.time.LocalDate

class CalendarMapper {

    val calendar: HashMap<LocalDate, DaySet> = HashMap<LocalDate, DaySet>()

    fun find(date: LocalDate): DaySet {
        return calendar.getValue(date)
    }

    fun update(date: LocalDate, daySet: DaySet) {
        var newDaySet: DaySet = daySet

        try {
            val oldDaySet = find(date)
            newDaySet = oldDaySet.plus(daySet)
        } catch (t: Throwable) {
            t.printStackTrace()
        } finally {
            calendar[date] = newDaySet
        }
    }

}