package church.catholic.liturgy.day

data class DayHash(
    val items: Set<DayID>,
    val litTemp: LitTemp
) {
    /** DayID 추가 */
    operator fun plus(id: DayID): DayHash = copy(items = items + id)

    /** Iterable<DayID> 추가 */
    operator fun plus(more: Iterable<DayID>): DayHash = copy(items = items + more)

    /** DayHash 병합 (litTemp 검증) */
    operator fun plus(other: DayHash): DayHash {
        val resultTemp = when {
            this.litTemp == other.litTemp -> this.litTemp
            this.litTemp == LitTemp.NONE   -> other.litTemp
            other.litTemp == LitTemp.NONE  -> this.litTemp
            else -> error("Liturgical time mismatch: ${this.litTemp} vs ${other.litTemp}")
        }
        return copy(items = items + other.items, litTemp = resultTemp)
    }

    // 한 곳(예: DayID.kt 끝부분)에서 재사용할 수 있게 두면 좋음
    val DAYID_GRADE_COMPARATOR: Comparator<DayID> =
        compareBy(
            { it.grade.classNum },   // 1,2,3... (작을수록 높음)
            { it.grade.classLine },  // 서브 순서
            { it.grade.mode },       // 변형(위령/기원 등) 구분
            { it.name }              // 동률 안정 정렬용
        )

    fun sortedItems(): List<DayID> {
        val list = items.sortedWith(DAYID_GRADE_COMPARATOR)
        return if(list[0].grade == LitGrade.MEMORIA_AD_LIBITUM) {
            list.reversed()
        } else {
            list
        }
    }

    fun topPriority(): DayID? =
        items.minWithOrNull(DAYID_GRADE_COMPARATOR) // 오름차순: 가장 앞이 ‘가장 높은 등급’

    companion object {
        /** vararg 팩토리 */
        fun of(vararg dayIDs: DayID, litTemp: LitTemp): DayHash =
            DayHash(dayIDs.toSet(), litTemp)
    }
}