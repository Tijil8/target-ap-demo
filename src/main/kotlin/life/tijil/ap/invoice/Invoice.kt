package life.tijil.ap.invoice

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class PaymentState { RECEIVED, SETTLED, FAILED }

/**
 * The payment ledger row. One invoice → one payment lifecycle, its state driven
 * by the Kafka consumer. This is the Postgres-backed application/payment state.
 */
@Entity
@Table(name = "payments")
class Payment(
    @Id
    @Column(name = "payment_id")
    val paymentId: String,

    @Column(name = "vendor_id")
    val vendorId: String,

    @Column(name = "amount")
    val amount: BigDecimal,

    @Column(name = "currency")
    val currency: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "state")
    var state: PaymentState = PaymentState.RECEIVED,

    @Column(name = "failure_reason")
    var failureReason: String? = null,

    /** Compact trace/log trail we later hand to the LLM for root-cause analysis. */
    @Column(name = "trace", columnDefinition = "text")
    var trace: String = "",

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        paymentId = "", vendorId = "", amount = BigDecimal.ZERO, currency = "USD",
    )

    fun appendTrace(line: String) {
        trace = if (trace.isBlank()) line else "$trace\n$line"
        updatedAt = Instant.now()
    }
}
