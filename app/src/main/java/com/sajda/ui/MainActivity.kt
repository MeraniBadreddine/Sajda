package com.sajda.ui

import com.sajda.BuildConfig
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sajda.R
import com.sajda.compass.AndroidDeclination
import com.sajda.compass.CompassEngine
import com.sajda.compass.Confidence
import com.sajda.data.UserLocation
import com.sajda.data.LocationStore
import com.sajda.geo.GeoPoint
import com.sajda.geo.MagneticHeadingDeg
import com.sajda.geo.QiblaEngine
import com.sajda.geo.QiblaSession
import com.sajda.geo.TrueBearingDeg
import com.sajda.geo.TrueHeadingDeg
import kotlin.math.abs
import java.util.Locale
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var compassView: CompassView
    private lateinit var degreeText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var locationText: TextView
    private lateinit var hintText: TextView
    private lateinit var deltaText: TextView
    private lateinit var calibrateBanner: TextView
    private lateinit var locationPill: TextView
    private lateinit var alignHaptic: AlignHaptic

    private lateinit var store: LocationStore
    private var session: QiblaSession? = null
    private var provisionalLocation = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var locationManager: LocationManager? = null
    private var activeListener: LocationListener? = null
    private var locating = false
    private var timeoutRunnable: Runnable? = null
    private var cancelSignal = CancellationSignal()
    private var locationRequestGeneration = 0
    private var lastFreshLocationAt = 0L
    private var isResumed = false
    private var lastUiHeadingAt = 0L
    private var lastUiHeading = Float.NaN
    private var permissionDeniedPermanently = false
    private val mainExecutor = Executor { command -> mainHandler.post(command) }

    private val compass: CompassEngine by lazy {
        CompassEngine(this, AndroidDeclination()) { reading ->
            runOnUiThread {
                if (isFinishing || isDestroyed || !isResumed) return@runOnUiThread
                val s = session ?: return@runOnUiThread
                val h = reading.trueHeadingDeg.toFloat()
                val now = System.currentTimeMillis()
                // ~30 Hz cap unless heading moved meaningfully
                if (!lastUiHeading.isNaN() &&
                    kotlin.math.abs(h - lastUiHeading) < 0.2f &&
                    now - lastUiHeadingAt < 33L
                ) {
                    return@runOnUiThread
                }
                lastUiHeading = h
                lastUiHeadingAt = now
                val conf = reading.confidence
                val heading = TrueHeadingDeg(reading.trueHeadingDeg)
                session = s.copy(
                    trueHeading = heading,
                    magneticHeading = MagneticHeadingDeg(reading.magneticHeadingDeg),
                    confidence = conf,
                    interferenceHint = reading.interferenceHint
                )
                renderSession()
            }
        }
    }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) {
            permissionDeniedPermanently = false
            startAutomaticLocation()
        } else {
            permissionDeniedPermanently = !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) &&
                !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            showPill(PillMode.NEED_PERMISSION, R.string.location_permission_pill, action = true)
            Toast.makeText(this, R.string.permission_rationale, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        store = LocationStore(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        compassView = findViewById(R.id.compass)
        degreeText = findViewById(R.id.degreeText)
        confidenceText = findViewById(R.id.confidenceText)
        locationText = findViewById(R.id.locationText)
        hintText = findViewById(R.id.hintText)
        deltaText = findViewById(R.id.deltaText)
        calibrateBanner = findViewById(R.id.calibrateBanner)
        alignHaptic = AlignHaptic(this)

        findViewById<View>(R.id.btnInfoWrap).setOnClickListener { showInfo() }
        // locationPill mirrors quiet status
        locationPill = findViewById(R.id.locationPill)
        locationPill.setOnClickListener { onLocationPillClick() }

        store.load()?.let { saved ->
            if (isValid(saved.lat, saved.lon)) applyPoint(
                GeoPoint(saved.lat, saved.lon), provisional = true, fixTimeMillis = saved.fixTimeMillis
            )
        }
        ensureLocation()
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        if (session != null) {
            compass.start()
            renderSession()
        }
        if (hasLocationPermission() && isSystemLocationEnabled()) {
            val needsRefresh = session == null || provisionalLocation ||
                System.currentTimeMillis() - lastFreshLocationAt > LOCATION_REFRESH_INTERVAL_MS
            if (needsRefresh && !locating) startAutomaticLocation()
        }
    }

    override fun onPause() {
        isResumed = false
        mainHandler.removeCallbacks(hideReadyRunnable)
        stopLocationUpdates(invalidate = true)
        compass.stop()
        session = session?.copy(
            trueHeading = null,
            magneticHeading = null,
            confidence = Confidence.CALIBRATE,
            interferenceHint = null
        )
        alignHaptic.reset()
        super.onPause()
    }

    override fun onDestroy() {
        stopLocationUpdates(invalidate = true)
        super.onDestroy()
    }

    private fun isValid(lat: Double, lon: Double) =
        GeoPoint(lat, lon).isValid()

    private fun isSystemLocationEnabled(): Boolean {
        val lm = locationManager ?: return false
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        if (!isSystemLocationEnabled()) {
            if (session == null) promptEnableSystemLocation()
            return
        }
        startAutomaticLocation()
    }

    private fun promptAppPermissionSettings() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.location_permission_denied)
            .setMessage(R.string.permission_rationale)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptEnableSystemLocation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.location_off_title)
            .setMessage(R.string.location_off_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (_: Exception) {
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun startAutomaticLocation() {
        if (locating || isFinishing) return
        if (!hasLocationPermission()) {
            showPill(PillMode.NEED_PERMISSION, R.string.location_permission_pill, action = true)
            return
        }
        if (!isSystemLocationEnabled()) {
            showPill(PillMode.NEED_SYSTEM, R.string.location_off_pill, action = true)
            if (session == null) promptEnableSystemLocation()
            return
        }

        locating = true
        val requestId = ++locationRequestGeneration
        if (session == null) showPill(PillMode.LOCATING, R.string.locating, action = false)

        val lm = locationManager
        if (lm == null) {
            completeLocationSearch(requestId, null)
            return
        }

        // A cached fix is useful for an immediate first render, but it remains provisional.
        if (session == null) {
            bestLastKnown(lm)?.let { cached ->
                applyPoint(GeoPoint(cached.latitude, cached.longitude), provisional = true, fixTimeMillis = cached.time)
            }
        }

        cancelSignal = CancellationSignal()
        timeoutRunnable = Runnable { completeLocationSearch(requestId, null) }
        mainHandler.postDelayed(timeoutRunnable!!, LOCATION_TIMEOUT_MS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestCurrentLocation(lm, requestId)
        } else {
            requestLegacyUpdates(lm, requestId)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation(lm: LocationManager, requestId: Int) {
        val providers: List<String> = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.distinct().filter { provider ->
                try { lm.isProviderEnabled(provider) } catch (_: Exception) { false }
            }
        if (providers.isEmpty()) {
            completeLocationSearch(requestId, null)
            return
        }

        // Query enabled providers together. A null network result must not suppress a GPS fix.
        var remaining = providers.size
        fun providerFinished(location: Location?) {
            if (requestId != locationRequestGeneration || !locating) return
            if (location != null && isUsableCurrentLocation(location)) {
                completeLocationSearch(requestId, location)
                return
            }
            remaining--
            if (remaining <= 0) completeLocationSearch(requestId, null)
        }
        providers.forEach { provider ->
            try {
                lm.getCurrentLocation(provider, cancelSignal, mainExecutor) { location ->
                    providerFinished(location)
                }
            } catch (_: SecurityException) {
                providerFinished(null)
            } catch (_: IllegalArgumentException) {
                providerFinished(null)
            } catch (_: RuntimeException) {
                providerFinished(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLegacyUpdates(lm: LocationManager, requestId: Int) {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (requestId != locationRequestGeneration || !locating) return
                if (isUsableCurrentLocation(location)) completeLocationSearch(requestId, location)
            }
            @Deprecated("Deprecated in API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        activeListener = listener
        var started = false
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            try {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper())
                    started = true
                }
            } catch (_: SecurityException) {
                completeLocationSearch(requestId, null)
                return
            } catch (_: Exception) {
                // Try the other provider.
            }
        }
        if (!started) completeLocationSearch(requestId, null)
    }

    private fun isUsableCurrentLocation(location: Location): Boolean {
        if (!isValid(location.latitude, location.longitude)) return false
        val age = System.currentTimeMillis() - location.time
        return age in -120_000L..CURRENT_FIX_MAX_AGE_MS
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(lm: LocationManager): Location? {
        val providers = mutableListOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) providers.add(0, LocationManager.FUSED_PROVIDER)
        val now = System.currentTimeMillis()
        return providers.mapNotNull { provider ->
            try { lm.getLastKnownLocation(provider) } catch (_: Exception) { null }
        }.filter { location ->
            isValid(location.latitude, location.longitude) &&
                (now - location.time) in -120_000L..CACHED_FIX_MAX_AGE_MS
        }.maxByOrNull { it.time }
    }

    private fun completeLocationSearch(requestId: Int, location: Location?) {
        if (requestId != locationRequestGeneration || !locating) return
        locating = false
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
        try { cancelSignal.cancel() } catch (_: Exception) {}
        activeListener?.let { listener ->
            try { locationManager?.removeUpdates(listener) } catch (_: Exception) {}
        }
        activeListener = null

        if (location != null && isUsableCurrentLocation(location)) {
            lastFreshLocationAt = System.currentTimeMillis()
            applyPoint(GeoPoint(location.latitude, location.longitude), provisional = false, fixTimeMillis = location.time)
        } else if (session == null) {
            showPill(PillMode.CACHED, R.string.location_timeout, action = true)
            Toast.makeText(this, R.string.location_timeout, Toast.LENGTH_LONG).show()
        } else {
            // A failed refresh must not change the age/trust state of the last good fix.
            renderSession()
            if (provisionalLocation) {
                showPill(PillMode.CACHED, R.string.location_cached, action = true)
            } else {
                updateLocationPillFromSession()
            }
        }
    }

    private fun stopLocationUpdates(invalidate: Boolean) {
        if (invalidate) locationRequestGeneration++
        locating = false
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
        try { cancelSignal.cancel() } catch (_: Exception) {}
        activeListener?.let {
            try { locationManager?.removeUpdates(it) } catch (_: Exception) {}
        }
        activeListener = null
    }

    private fun applyPoint(
        point: GeoPoint,
        provisional: Boolean,
        fixTimeMillis: Long = System.currentTimeMillis()
    ) {
        if (!point.isValid()) return
        provisionalLocation = provisional
        val qibla = QiblaEngine.solve(point)
        store.save(UserLocation(point.latDeg, point.lonDeg, fixTimeMillis))
        if (!provisional) lastFreshLocationAt = System.currentTimeMillis()
        session = QiblaSession(
            point = point,
            qibla = qibla,
            trueHeading = session?.trueHeading,
            magneticHeading = session?.magneticHeading,
            confidence = session?.confidence ?: Confidence.CALIBRATE,
            interferenceHint = session?.interferenceHint,
            locationProvisional = provisional
        )
        compass.setLocation(point.latDeg, point.lonDeg)
        if (isResumed && !isFinishing) compass.start()
        renderSession()
    }

    /**
     * Single place that maps session → UI.
     * When confidence is low, do not pretend the heading is precise.
     */
    private fun renderSession() {
        val s = session ?: return

        // Qibla angle is mathematical — always show (1 decimal when stable location)
        degreeText.text = String.format(Locale.US, "%.1f°", s.qibla.value)
        degreeText.alpha = 1f

        compassView.qiblaBearing = s.qibla.value.toFloat()
        compassView.contentDescription = getString(R.string.compass_accessibility, degreeText.text)
        val heading = s.trueHeading
        compassView.uncertain = heading == null || !s.confidence.allowsPreciseHeading
        if (heading != null) {
            compassView.deviceHeading = heading.value.toFloat()
        }

        if (!compass.isAvailable || compass.registrationFailed) {
            compassView.uncertain = true
            compassView.alignment = 0f
            degreeText.setTextColor(ContextCompat.getColor(this, R.color.label_primary))
            deltaText.text = getString(R.string.heading_approximate)
            calibrateBanner.visibility = View.GONE
            confidenceText.text = getString(R.string.conf_calibrate)
            confidenceText.setTextColor(ContextCompat.getColor(this, R.color.conf_low))
            hintText.text = getString(R.string.sensor_unavailable)
            hintText.visibility = View.VISIBLE
            updateLocationPillFromSession()
            return
        }

        // Turn guidance only when we trust the heading enough
        if (heading == null || !s.confidence.allowsPreciseHeading) {
            alignHaptic.reset()
            deltaText.text = when {
                s.confidence.needsCalibration -> getString(R.string.calibrate_guidance)
                else -> getString(R.string.heading_approximate)
            }
            calibrateBanner.visibility = if (s.confidence.needsCalibration) View.VISIBLE else View.GONE
            if (s.confidence.needsCalibration) {
                calibrateBanner.text = getString(R.string.calibrate_banner)
            }
        } else {
            calibrateBanner.visibility = View.GONE
            val turn = s.turnToQibla()?.value ?: 0.0
            alignHaptic.onTurn(abs(turn), threshold = 2.0)
            // 0 outside 12°, 1 inside 2° — smooth visual lock
            val align = when {
                abs(turn) >= 12.0 -> 0f
                abs(turn) <= 2.0 -> 1f
                else -> (1f - ((abs(turn) - 2.0) / 10.0).toFloat()).coerceIn(0f, 1f)
            }
            compassView.alignment = align
            if (abs(turn) < 2.0) {
                degreeText.setTextColor(ContextCompat.getColor(this, R.color.accent))
            } else {
                degreeText.setTextColor(ContextCompat.getColor(this, R.color.label_primary))
            }
            deltaText.text = when {
                abs(turn) < 2.0 -> getString(R.string.aligned)
                else -> getString(
                    R.string.turn_degrees,
                    abs(turn).toInt(),
                    if (turn > 0) getString(R.string.right) else getString(R.string.left)
                )
            }
        }

        when (s.confidence) {
            Confidence.HIGH -> {
                // Pro silence: no status when sensors are trustworthy
                confidenceText.visibility = View.GONE
            }
            else -> {
                confidenceText.visibility = View.VISIBLE
                confidenceText.text = when (s.confidence) {
                    Confidence.MEDIUM -> "●  " + getString(R.string.conf_medium)
                    Confidence.LOW -> "●  " + getString(R.string.conf_low)
                    Confidence.CALIBRATE -> "●  " + getString(R.string.conf_calibrate)
                    Confidence.HIGH -> ""
                }
                confidenceText.setTextColor(
                    ContextCompat.getColor(
                        this,
                        when (s.confidence) {
                            Confidence.MEDIUM -> R.color.conf_medium
                            Confidence.LOW, Confidence.CALIBRATE -> R.color.conf_low
                            Confidence.HIGH -> R.color.conf_high
                        }
                    )
                )
            }
        }

        val hint = s.interferenceHint
        hintText.text = hint ?: ""
        hintText.visibility = if (hint != null) View.VISIBLE else View.GONE

        // Quiet location: no raw coordinates on the main stage
        updateLocationPillFromSession()
    }


    private enum class PillMode { HIDDEN, LOCATING, CACHED, READY_FADE, NEED_PERMISSION, NEED_SYSTEM }

    private var pillMode = PillMode.HIDDEN
    /** After READY has been shown and hidden, stay quiet until location quality changes. */
    private var readyDismissed = false
    private var lastProvisional: Boolean? = null
    private val hideReadyRunnable = Runnable {
        if (pillMode != PillMode.READY_FADE) return@Runnable
        locationPill.animate().cancel()
        locationPill.animate()
            .alpha(0f)
            .setDuration(320)
            .withEndAction {
                if (pillMode == PillMode.READY_FADE) {
                    locationPill.visibility = View.GONE
                    locationPill.alpha = 1f
                    pillMode = PillMode.HIDDEN
                    readyDismissed = true
                }
            }
            .start()
    }

    private fun onLocationPillClick() {
        when (pillMode) {
            PillMode.NEED_PERMISSION -> {
                if (permissionDeniedPermanently) promptAppPermissionSettings() else ensureLocation()
            }
            PillMode.NEED_SYSTEM -> promptEnableSystemLocation()
            PillMode.CACHED -> {
                if (!locating) startAutomaticLocation()
            }
            else -> Unit
        }
    }

    /**
     * Only mutates the pill when the *mode* changes.
     * Prevents sensor ticks from resetting the READY fade timer.
     */
    private fun showPill(mode: PillMode, textRes: Int, action: Boolean) {
        if (mode == pillMode && mode != PillMode.LOCATING) {
            // Same mode — keep text in sync only for locating counter states
            return
        }
        mainHandler.removeCallbacks(hideReadyRunnable)
        locationPill.animate().cancel()
        pillMode = mode

        if (mode == PillMode.HIDDEN) {
            locationPill.visibility = View.GONE
            locationPill.alpha = 1f
            return
        }

        locationPill.alpha = 1f
        locationPill.visibility = View.VISIBLE
        locationPill.setText(textRes)
        locationText.setText(textRes)
        locationPill.setBackgroundResource(
            if (action) R.drawable.bg_liquid_glass_action else R.drawable.bg_liquid_glass_pill
        )
        locationPill.setTextColor(
            ContextCompat.getColor(
                this,
                if (action) android.R.color.white else R.color.label_secondary
            )
        )
        locationPill.isClickable = action || mode == PillMode.CACHED

        if (mode == PillMode.READY_FADE) {
            mainHandler.postDelayed(hideReadyRunnable, 2200L)
        }
    }

    private fun updateLocationPillFromSession() {
        val s = session
        when {
            !hasLocationPermission() -> {
                readyDismissed = false
                lastProvisional = null
                showPill(PillMode.NEED_PERMISSION, R.string.location_permission_pill, action = true)
            }
            !isSystemLocationEnabled() -> {
                readyDismissed = false
                lastProvisional = null
                showPill(PillMode.NEED_SYSTEM, R.string.location_off_pill, action = true)
            }
            s == null -> {
                readyDismissed = false
                showPill(PillMode.LOCATING, R.string.locating, action = false)
            }
            s.locationProvisional -> {
                readyDismissed = false
                lastProvisional = true
                showPill(PillMode.CACHED, R.string.location_cached, action = false)
            }
            else -> {
                // Fresh fix. Show READY only once per "became ready" transition.
                val wasProv = lastProvisional
                lastProvisional = false
                if (readyDismissed && pillMode == PillMode.HIDDEN) {
                    return // stay quiet
                }
                if (wasProv == true || pillMode == PillMode.LOCATING || pillMode == PillMode.CACHED
                    || pillMode == PillMode.NEED_PERMISSION || pillMode == PillMode.NEED_SYSTEM
                    || pillMode == PillMode.HIDDEN && !readyDismissed
                    || pillMode == PillMode.READY_FADE
                ) {
                    if (pillMode != PillMode.READY_FADE) {
                        showPill(PillMode.READY_FADE, R.string.location_ready, action = false)
                    }
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun showInfo() {
        val view = layoutInflater.inflate(R.layout.dialog_about, null)
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            try { BuildConfig.VERSION_NAME } catch (_: Exception) { "1.9.0" }
        }
        view.findViewById<TextView>(R.id.aboutVersion).text =
            getString(R.string.about_version, versionName)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()
        LiquidGlass.applyDialogBackdrop(dialog)
        view.findViewById<TextView>(R.id.aboutOk).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 25_000L
        const val CURRENT_FIX_MAX_AGE_MS = 120_000L
        const val CACHED_FIX_MAX_AGE_MS = 24L * 60L * 60L * 1000L
        const val LOCATION_REFRESH_INTERVAL_MS = 15L * 60L * 1000L
    }

}