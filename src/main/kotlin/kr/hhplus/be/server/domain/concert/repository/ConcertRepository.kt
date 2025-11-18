package kr.hhplus.be.server.domain.concert.repository

import kr.hhplus.be.server.domain.concert.model.Concert

interface ConcertRepository {
    fun save(concert: Concert): Concert
    fun findById(id: Long): Concert?
    fun findByIdOrThrow(id: Long): Concert
    fun findAll(): List<Concert>
}
