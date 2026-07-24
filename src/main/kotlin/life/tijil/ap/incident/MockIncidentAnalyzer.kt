package life.tijil.ap.incident

import life.tijil.ap.invoice.Payment
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Offline analyzer. Reads the real failure reason and trace off the payment and turns
 * them into a structured, plain-English root cause + fix. No API key, no network — so the
 * service runs anywhere. It genuinely reasons over the same inputs the Claude analyzer gets.
 */
@Profile("!claude")
@Component
class MockIncidentAnalyzer : IncidentAnalyzer {

    override fun analyze(payment: Payment): IncidentAnalysis {
        val reason = payment.failureReason ?: "unknown"
        val fix = when {
            reason.contains("not approved") ->
                "Onboard the vendor in the vendor master (or warm the vendor cache), then replay invoices.received for this payment."
            reason.contains("exceeds auto-approve ceiling") ->
                "Route to the manual-approval queue; once approved, re-emit the invoice event to settle."
            reason.contains("currency") ->
                "Convert to USD upstream, or enable the multi-currency settlement rail for this vendor."
            reason.contains("must be positive") ->
                "Reject at the API boundary with validation; the invoice payload carried a non-positive amount."
            else -> "Inspect the payment trace and the consumer logs around the failure timestamp."
        }
        return IncidentAnalysis(
            paymentId = payment.paymentId,
            rootCause = "Payment ${payment.paymentId} failed because: $reason",
            suggestedFix = fix,
            analyzedBy = "mock",
        )
    }
}
