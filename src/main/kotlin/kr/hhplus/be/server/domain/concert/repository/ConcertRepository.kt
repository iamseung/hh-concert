package kr.hhplus.be.server.domain.concert.repository

import kr.hhplus.be.server.domain.concert.model.ConcertModel

interface ConcertRepository {
    fun save(concertModel: ConcertModel): ConcertModel
    fun findById(id: Long): ConcertModel?
    fun findByIdOrThrow(id: Long): ConcertModel
    fun findAll(): List<ConcertModel>
}
