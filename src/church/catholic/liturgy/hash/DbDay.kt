package church.catholic.liturgy.hash

@JvmRecord
data class DbDay(
    override val idCat: IdCat,
    override val scope: String,
    override val litGrade: LitGrade,
    override val litColour: LitColour,
    override val idName: String,

    override val dayName: String
): LitDay {

}