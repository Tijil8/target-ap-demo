package life.tijil.ap.payment

import java.math.BigDecimal
import life.tijil.ap.invoice.InvoiceReceived
import life.tijil.ap.invoice.PaymentRepository
import life.tijil.ap.invoice.PaymentState
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

/**
 * The event-driven core: consumes invoices.received, validates against the vendor
 * cache and business rules, writes state to Postgres, and emits payments.settled
 * or payments.failed. Every decision is written to the payment's trace so the LLM
 * incident-triage endpoint has real logs to reason over.
 */
@Component
class PaymentProcessor(
    private val payments: PaymentRepository,
    private val vendors: VendorCache,
    private val kafka: KafkaTemplate<String, Any>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // A soft limit that mirrors a real "requires manual approval above threshold" rule.
    private val autoApproveCeiling = BigDecimal("10000")

    @EventListener(ApplicationReadyEvent::class)
    fun onStartup() = vendors.seedIfEmpty()

    @KafkaListener(
        topics = ["\${ap.topics.invoices-received}"],
        containerFactory = "invoiceListenerContainerFactory",
    )
    fun onInvoice(
        @Payload invoice: InvoiceReceived,
        @Header(KafkaHeaders.RECEIVED_KEY, required = false) key: String?,
    ) {
        val payment = payments.findById(invoice.paymentId).orElse(null) ?: run {
            log.warn("payment {} not found for consumed invoice", invoice.paymentId)
            return
        }

        val cacheHit = vendors.wasCacheHit(invoice.vendorId)
        payment.appendTrace("consumer picked up invoice; vendor cache ${if (cacheHit) "HIT" else "MISS"}")

        val failure = validate(invoice)
        if (failure != null) {
            payment.state = PaymentState.FAILED
            payment.failureReason = failure
            payment.appendTrace("validation FAILED: $failure")
            payments.save(payment)
            kafka.send("payments.failed", payment.paymentId, mapOf("paymentId" to payment.paymentId, "reason" to failure))
            log.info("payment {} failed: {}", payment.paymentId, failure)
            return
        }

        payment.state = PaymentState.SETTLED
        payment.appendTrace("validation passed; settling ${payment.amount} ${payment.currency} to ${payment.vendorId}")
        payments.save(payment)
        kafka.send("payments.settled", payment.paymentId, mapOf("paymentId" to payment.paymentId))
        log.info("payment {} settled", payment.paymentId)
    }

    /** Returns a failure reason, or null if the invoice is payable. */
    private fun validate(invoice: InvoiceReceived): String? = when {
        !vendors.isApproved(invoice.vendorId) ->
            "vendor ${invoice.vendorId} is not approved (not present in vendor master cache)"
        invoice.amount <= BigDecimal.ZERO ->
            "invoice amount must be positive, got ${invoice.amount}"
        invoice.amount > autoApproveCeiling ->
            "amount ${invoice.amount} exceeds auto-approve ceiling $autoApproveCeiling; requires manual approval"
        invoice.currency != "USD" ->
            "currency ${invoice.currency} not supported in this settlement rail (USD only)"
        else -> null
    }
}
