package kr.hhplus.be.server.common.exception

/**
 * 분산 락 획득 실패 예외
 *
 * 사용 예:
 * - throw LockAcquisitionException(ErrorCode.LOCK_ACQUISITION_FAILED, "reservation:lock:seat:1")
 */
class LockAcquisitionException(
    errorCode: ErrorCode,
    val lockKey: String? = null,
) : BusinessException(errorCode) {
    override val message: String
        get() = if (lockKey != null) {
            "${errorCode.message} (lockKey=$lockKey)"
        } else {
            errorCode.message
        }
}
