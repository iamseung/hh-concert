package kr.hhplus.be.server.domain.point.repository

import kr.hhplus.be.server.domain.point.model.PointModel

interface PointRepository {
    fun save(pointModel: PointModel): PointModel
    fun findById(id: Long): PointModel?
    fun findByIdOrThrow(id: Long): PointModel
    fun findByUserId(userId: Long): PointModel?
    fun findByUserIdOrThrow(userId: Long): PointModel
    fun findByUserIdWithLock(userId: Long): PointModel?
}
