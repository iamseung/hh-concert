package kr.hhplus.be.server.common.util

import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.common.exception.NotFoundException
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.findByIdOrNull

/**
 * ID로 Entity를 조회하며, 값이 없을 경우 NotFoundException을 발생시킵니다.
 *
 * @param id 조회할 Entity의 ID
 * @param errorCode 발생시킬 에러 코드
 * @return 조회된 Entity
 * @throws NotFoundException Entity를 찾을 수 없는 경우
 *
 * @example
 * ```kotlin
 * val concert = concertRepository.findByIdOrThrow(concertId, ErrorCode.CONCERT_NOT_FOUND)
 * ```
 */
inline fun <reified T : Any, ID> CrudRepository<T, ID>.findByIdOrThrow(id: ID): T {
    return this.findByIdOrNull(id)
        ?: throw NotFoundException(ErrorCode.ENTITY_NOT_FOUND)
}
