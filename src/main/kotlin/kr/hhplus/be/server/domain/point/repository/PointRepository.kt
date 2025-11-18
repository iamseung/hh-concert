package kr.hhplus.be.server.domain.point.repository

import kr.hhplus.be.server.domain.point.model.Point

interface PointRepository {
    fun save(point: Point): Point
    fun findById(id: Long): Point?
    fun findByIdOrThrow(id: Long): Point
    fun findByUserId(userId: Long): Point?
    fun findByUserIdOrThrow(userId: Long): Point
    fun findByUserIdWithLock(userId: Long): Point?
}
