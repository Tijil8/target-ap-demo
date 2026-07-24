package life.tijil.ap.invoice

import java.math.BigDecimal
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class InvoiceRequest(
    val vendorId: String,
    val amount: BigDecimal,
    val currency: String = "USD",
)

/** Event published onto Kafka; the PaymentProcessor consumes it. */
data class InvoiceReceived(
    val paymentId: String,
    val vendorId: String,
    val amount: BigDecimal,
    val currency: String,
)

@RestController
@RequestMapping("/invoices")
class InvoiceController(
    private val kafka: KafkaTemplate<String, Any>,
    private val payments: PaymentRepository,
) {
    @PostMapping
    fun submit(@RequestBody req: InvoiceRequest): ResponseEntity<Map<String, String>> {
        val paymentId = "pay_" + UUID.randomUUID().toString().substring(0, 8)

        // Persist the initial ledger row, then hand off to the event stream.
        val payment = Payment(
            paymentId = paymentId,
            vendorId = req.vendorId,
            amount = req.amount,
            currency = req.currency,
        ).apply { appendTrace("invoice received: vendor=${req.vendorId} amount=${req.amount} ${req.currency}") }
        payments.save(payment)

        kafka.send(
            "invoices.received",
            paymentId,
            InvoiceReceived(paymentId, req.vendorId, req.amount, req.currency),
        )

        return ResponseEntity.accepted().body(mapOf("paymentId" to paymentId, "state" to "RECEIVED"))
    }

    @GetMapping("/{paymentId}")
    fun status(@PathVariable paymentId: String): ResponseEntity<Payment> =
        payments.findById(paymentId)
            .map { ResponseEntity.ok(it) }
            .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build())
}
