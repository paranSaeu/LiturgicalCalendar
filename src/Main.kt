import church.catholic.liturgy.LiturgicalCalendar
import church.catholic.liturgy.day.DayCat
import church.catholic.liturgy.db.IdRepo
import java.time.Year

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {

    val lc = LiturgicalCalendar(Year.of(2008), "ko_KR")

    lc.test()

}