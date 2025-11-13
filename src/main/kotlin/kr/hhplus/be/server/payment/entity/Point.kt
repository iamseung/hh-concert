package kr.hhplus.be.server.payment.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import kr.hhplus.be.server.common.BaseEntity
import kr.hhplus.be.server.user.User

@Entity
class Point(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    // 포인트 잔액
    val balance: Int,
) : BaseEntity(){

}