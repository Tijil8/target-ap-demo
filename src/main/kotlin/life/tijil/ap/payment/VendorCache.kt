package life.tijil.ap.payment

import java.time.Duration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * Redis-backed vendor lookup cache. In a real AP system the vendor master lives
 * in another service; here we cache "is this vendor approved for payment" to avoid
 * hammering that source on every invoice.
 */
@Component
class VendorCache(private val redis: StringRedisTemplate) {

    /** Seed a couple of sample vendors so the happy path works out of the box. */
    fun seedIfEmpty() {
        approve("VENDOR-ACME")
        approve("VENDOR-GLOBEX")
    }

    fun approve(vendorId: String) {
        redis.opsForValue().set(key(vendorId), "APPROVED", Duration.ofHours(1))
    }

    fun isApproved(vendorId: String): Boolean =
        redis.opsForValue().get(key(vendorId)) == "APPROVED"

    fun wasCacheHit(vendorId: String): Boolean = redis.hasKey(key(vendorId))

    private fun key(vendorId: String) = "vendor:$vendorId:status"
}
