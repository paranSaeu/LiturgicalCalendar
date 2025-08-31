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
        require(this.litTemp == other.litTemp) {
            "Liturgical time mismatch: ${this.litTemp} vs ${other.litTemp}"
        }
        return copy(items = items + other.items)
    }

    companion object {
        /** vararg 팩토리 */
        fun of(vararg dayIDs: DayID, litTemp: LitTemp): DayHash =
            DayHash(dayIDs.toSet(), litTemp)
    }
}