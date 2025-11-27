package kr.hhplus.be.server.application.usecase.point

data class ChargePointCommand(
    val userId: Long,
    val amount: Int
)
