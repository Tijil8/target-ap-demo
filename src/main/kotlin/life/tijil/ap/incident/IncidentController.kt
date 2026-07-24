package life.tijil.ap.incident

import life.tijil.ap.invoice.PaymentRepository
import life.tijil.ap.invoice.PaymentState
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/incidents")
class IncidentController(
    private val payments: PaymentRepository,
    private val analyzer: IncidentAnalyzer,
) {
    /**
     * Explain why a payment failed, in plain English, from its logs + trace.
     * GET /incidents/{paymentId}/explain
     */
    @GetMapping("/{paymentId}/explain")
    fun explain(@PathVariable paymentId: String): ResponseEntity<Any> {
        val payment = payments.findById(paymentId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "payment $paymentId not found"))

        if (payment.state != PaymentState.FAILED) {
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "payment $paymentId is ${payment.state}, not FAILED — nothing to triage"))
        }

        return ResponseEntity.ok(analyzer.analyze(payment))
    }
}
