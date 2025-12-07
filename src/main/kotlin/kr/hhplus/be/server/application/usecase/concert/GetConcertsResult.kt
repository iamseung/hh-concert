package kr.hhplus.be.server.application.usecase.concert

data class GetConcertsResult(
    val concerts: List<ConcertInfo>,
) {
    data class ConcertInfo(
        val concertId: Long,
        val title: String,
        val description: String?,
    )
}
