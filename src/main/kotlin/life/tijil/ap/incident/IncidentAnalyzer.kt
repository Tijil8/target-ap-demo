package life.tijil.ap.incident

import life.tijil.ap.invoice.Payment

/** Root-cause analysis for a failed payment. */
data class IncidentAnalysis(
    val paymentId: String,
    val rootCause: String,
    val suggestedFix: String,
    val analyzedBy: String,
)

/**
 * Pluggable incident triage. Two implementations:
 *   - MockIncidentAnalyzer   (profile: default) — no API key, fully offline, deterministic.
 *   - ClaudeIncidentAnalyzer (profile: claude)  — real Anthropic call over the payment's logs.
 *
 * The endpoint code is identical either way; only the wiring changes. That means the
 * service runs with one `docker-compose up`, and flipping on a real LLM is a profile flag.
 */
interface IncidentAnalyzer {
    fun analyze(payment: Payment): IncidentAnalysis
}
