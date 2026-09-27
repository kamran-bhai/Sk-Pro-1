package com.protectfinanceddevices.app.core.network

/**
 * Enterprise Backend API Configuration
 * Anchored to the live deployment authority.
 */
object ApiConfig {
    const val DEFAULT_BASE_URL = "https://ais-dev-tm5ls67gqw2nrygmvrama2-896573692943.asia-southeast1.run.app"
    const val API_PREFIX = "/api/v1"

    // Endpoints
    const val ENDPOINT_ENROLLMENT_DISCLOSURE = "$API_PREFIX/enrollments" // GET /:code
    const val ENDPOINT_ENROLLMENT_CHALLENGE = "$API_PREFIX/enrollments"  // POST /:id/challenge
    const val ENDPOINT_ENROLLMENT_VERIFY = "$API_PREFIX/enrollments"     // POST /:id/verify
    const val ENDPOINT_HEARTBEAT = "$API_PREFIX/device/heartbeat"         // POST
    const val ENDPOINT_DEVICE_STATUS = "$API_PREFIX/devices"             // GET /:enrollmentId/status

    // App constants
    const val CLIENT_APP_VERSION = "1.0.0"
}
