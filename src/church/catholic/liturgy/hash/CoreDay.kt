package church.catholic.liturgy.hash

enum class CoreDay(
    idStr: String
): LitDay{

    /* === 열거체 상수 구역 === */



    DOMINICA_PASCHALIS("mov.psch.110.001.w01d0"),



    ;

    /* === 정의 구역 === */

    override val idCat: IdCat
    override val scope: String
    override val litGrade: LitGrade
    override val litColour: LitColour
    override val idName: String

    override val dayName: String?

    init {

        if(!LitDay.validate(idStr)) error("Invalid ID : $idStr")

        val d = LitDay.decode(idStr)

        idCat = d.idCat
        scope = d.scope
        litGrade = d.litGrade
        litColour = d.litColour
        idName = d.idName

        dayName = ""
    }

}