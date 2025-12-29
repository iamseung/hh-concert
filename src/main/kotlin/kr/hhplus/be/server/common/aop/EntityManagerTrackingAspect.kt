package kr.hhplus.be.server.common.aop

import jakarta.persistence.EntityManager
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class EntityManagerTrackingAspect(
    private val entityManager: EntityManager,
) {

    companion object {
        val entityManagerIds = mutableListOf<EntityManagerSnapshot>()

        fun clear() = entityManagerIds.clear()
    }

    data class EntityManagerSnapshot(
        val methodName: String,
        val entityManagerHashCode: Int,
        val isOpen: Boolean,
        val isJoinedToTransaction: Boolean,
    )

    @Around("execution(* kr.hhplus.be.server.domain..repository.*Repository.*(..))")
    fun trackEntityManager(joinPoint: ProceedingJoinPoint): Any? {
        val methodName = "${joinPoint.signature.declaringType.simpleName}.${joinPoint.signature.name}"

        // EntityManager는 트랜잭션마다 다른 프록시/인스턴스
        val snapshot = EntityManagerSnapshot(
            methodName = methodName,
            entityManagerHashCode = System.identityHashCode(entityManager.delegate),
            isOpen = entityManager.isOpen,
            isJoinedToTransaction = entityManager.isJoinedToTransaction,
        )

        entityManagerIds.add(snapshot)

        return joinPoint.proceed()
    }
}
