package life.tijil.ap.incident

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import life.tijil.ap.invoice.Payment
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Real LLM incident triage. Feeds the failed payment's logs + trace to Claude and asks
 * for a plain-English root cause and a concrete fix — applying an LLM to production
 * incident investigation, as working code.
 *
 * Enabled only under the `claude` Spring profile so the default path needs no API key.
 * Reads ANTHROPIC_API_KEY from the environment via AnthropicOkHttpClient.fromEnv().
 */
@Profile("claude")
@Component
class ClaudeIncidentAnalyzer : IncidentAnalyzer {

    private val log = LoggerFactory.getLogger(javaClass)
    private val client: AnthropicClient = AnthropicOkHttpClient.fromEnv()

    private val model = "claude-haiku-4-5"

    override fun analyze(payment: Payment): IncidentAnalysis {
        val prompt = buildString {
            appendLine("You are an on-call engineer for an Accounts Payable service.")
            appendLine("A vendor payment failed. Using ONLY the state and trace below, give:")
            appendLine("1. the root cause in one plain-English sentence")
            appendLine("2. one concrete remediation step")
            appendLine()
            appendLine("Respond in exactly this format:")
            appendLine("ROOT CAUSE: <one sentence>")
            appendLine("SUGGESTED FIX: <one step>")
            appendLine()
            appendLine("paymentId:     ${payment.paymentId}")
            appendLine("vendorId:      ${payment.vendorId}")
            appendLine("amount:        ${payment.amount} ${payment.currency}")
            appendLine("state:         ${payment.state}")
            appendLine("failureReason: ${payment.failureReason}")
            appendLine("--- trace ---")
            appendLine(payment.trace)
        }

        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(1024L)
            .addUserMessage(prompt)
            .build()

        val text = client.messages().create(params).content().stream()
            .flatMap { it.text().stream() }
            .map { it.text() }
            .reduce("") { a, b -> a + b }

        log.info("claude triage for {}:\n{}", payment.paymentId, text)
        return parse(payment, text)
    }

    private fun parse(payment: Payment, text: String): IncidentAnalysis {
        val rootCause = extract(text, "ROOT CAUSE:")
            ?: text.trim().ifBlank { "Claude returned no analysis." }
        val fix = extract(text, "SUGGESTED FIX:")
            ?: "See root cause; inspect the consumer logs around the failure."
        return IncidentAnalysis(
            paymentId = payment.paymentId,
            rootCause = rootCause,
            suggestedFix = fix,
            analyzedBy = "claude:$model",
        )
    }

    private fun extract(text: String, label: String): String? =
        text.lineSequence()
            .firstOrNull { it.trimStart().startsWith(label) }
            ?.substringAfter(label)
            ?.trim()
            ?.ifBlank { null }
}
