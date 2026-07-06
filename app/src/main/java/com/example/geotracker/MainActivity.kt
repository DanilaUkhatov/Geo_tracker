package com.example.geotracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import com.example.geotracker.data.AuthRepository
import com.example.geotracker.data.SyncSettingsRepository
import com.example.geotracker.data.VisitObject
import com.example.geotracker.data.VisitRepository
import com.example.geotracker.data.VisitStatus
import com.example.geotracker.geofence.GeofenceBroadcastReceiver
import com.example.geotracker.geofence.GeofenceManager
import com.example.geotracker.sync.EventSyncManager

class MainActivity : Activity() {
    private lateinit var geofenceManager: GeofenceManager
    private val eventSyncManager = EventSyncManager()
    private lateinit var objectListContainer: LinearLayout
    private lateinit var registrationStatusText: TextView
    private lateinit var syncStatusText: TextView
    private var lastRegisteredObjectsSignature: String? = null
    private val visitStatusChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Visit status changed broadcast received")
            renderObjects()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AuthRepository.initialize(this)
        SyncSettingsRepository.initialize(this)
        VisitRepository.initialize(this)
        geofenceManager = GeofenceManager(this)

        if (AuthRepository.isDeviceAuthorized()) {
            showMainScreen()
        } else {
            showDeviceNotAuthorizedScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        VisitRepository.initialize(this)
        if (AuthRepository.isDeviceAuthorized()) {
            renderObjects()
            ensureGeofencesRegistered(requestPermissionsIfMissing = false)
            autoSyncEvents()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()

        val filter = IntentFilter(GeofenceBroadcastReceiver.ACTION_VISIT_STATUS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(visitStatusChangedReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(visitStatusChangedReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(visitStatusChangedReceiver)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_FINE_LOCATION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                requestBackgroundLocationIfNeeded()
                ensureGeofencesRegistered(requestPermissionsIfMissing = false)
            } else {
                showToast("ACCESS_FINE_LOCATION не выдан")
            }
        }

        if (requestCode == REQUEST_BACKGROUND_LOCATION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                showToast("ACCESS_BACKGROUND_LOCATION выдан")
                ensureGeofencesRegistered(requestPermissionsIfMissing = false)
            } else {
                showToast("ACCESS_BACKGROUND_LOCATION не выдан")
            }
        }
    }

    private fun createContentView(): View {
        val rootScrollView = ScrollView(this)
        val rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        val titleText = TextView(this).apply {
            text = "Трекер посещения объектов"
            textSize = 22f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.rgb(28, 28, 28))
        }

        registrationStatusText = TextView(this).apply {
            text = "Геозоны еще не зарегистрированы"
            textSize = 14f
            setTextColor(Color.rgb(90, 90, 90))
            setPadding(0, dp(8), 0, dp(12))
        }

        syncStatusText = TextView(this).apply {
            text = buildSyncStatusText()
            textSize = 14f
            setTextColor(Color.rgb(90, 90, 90))
            setPadding(0, 0, 0, dp(12))
        }

        val syncButton = Button(this).apply {
            text = "Синхронизировать события"
            setOnClickListener { syncEvents() }
        }

        val syncSettingsButton = Button(this).apply {
            text = "Настроить URL синхронизации"
            setOnClickListener { showSyncUrlDialog() }
        }

        val addObjectButton = Button(this).apply {
            text = "Добавить место"
            setOnClickListener { showAddObjectDialog() }
        }

        objectListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }

        rootContainer.addView(titleText)
        rootContainer.addView(registrationStatusText)
        rootContainer.addView(syncStatusText)
        rootContainer.addView(syncButton)
        rootContainer.addView(syncSettingsButton)
        rootContainer.addView(addObjectButton)
        rootContainer.addView(objectListContainer)
        rootScrollView.addView(rootContainer)

        return rootScrollView
    }

    private fun showDeviceNotAuthorizedScreen() {
        setContentView(createDeviceNotAuthorizedContentView())
    }

    private fun showMainScreen() {
        setContentView(createContentView())
        requestRequiredPermissions()
        renderObjects()
        ensureGeofencesRegistered(requestPermissionsIfMissing = false)
        autoSyncEvents()
    }

    private fun createDeviceNotAuthorizedContentView(): View {
        val rootScrollView = ScrollView(this)
        val rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(24))
        }

        val titleText = TextView(this).apply {
            text = "Устройство не авторизовано"
            textSize = 24f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.rgb(28, 28, 28))
        }

        val androidIdText = TextView(this).apply {
            text = "ANDROID_ID: ${AuthRepository.getAndroidId()}"
            textSize = 14f
            setTextColor(Color.rgb(55, 55, 55))
            setPadding(0, dp(12), 0, dp(4))
        }

        val androidIdHashText = TextView(this).apply {
            text = "ANDROID_ID hash: ${AuthRepository.getAndroidIdHash()}"
            textSize = 14f
            setTextColor(Color.rgb(55, 55, 55))
            setPadding(0, dp(4), 0, dp(12))
        }

        val retryButton = Button(this).apply {
            text = "Проверить снова"
            setOnClickListener {
                if (AuthRepository.isDeviceAuthorized()) {
                    showMainScreen()
                } else {
                    showToast("Устройство не входит в корпоративный allowlist")
                }
            }
        }

        rootContainer.addView(titleText)
        rootContainer.addView(androidIdText)
        rootContainer.addView(androidIdHashText)
        rootContainer.addView(retryButton)
        rootScrollView.addView(rootContainer)

        return rootScrollView
    }

    private fun renderObjects() {
        if (!::objectListContainer.isInitialized) return

        if (::syncStatusText.isInitialized) {
            syncStatusText.text = buildSyncStatusText()
        }

        objectListContainer.removeAllViews()
        VisitRepository.getObjects().forEach { visitObject ->
            objectListContainer.addView(createObjectView(visitObject))
        }
    }

    private fun createObjectView(visitObject: VisitObject): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(220, 220, 220))
            }
        }

        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(10)
        }
        container.layoutParams = layoutParams

        val objectTitle = TextView(this).apply {
            text = "Объект #${visitObject.objectId}"
            textSize = 18f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.rgb(30, 30, 30))
        }

        val address = TextView(this).apply {
            text = visitObject.address
            textSize = 14f
            setTextColor(Color.rgb(85, 85, 85))
            setPadding(0, dp(4), 0, dp(8))
        }

        container.addView(objectTitle)
        container.addView(address)
        container.addView(createFieldText("Статус", statusText(visitObject.status)))
        container.addView(createFieldText("Радиус геозоны", "${visitObject.radiusMeters.toInt()} м"))
        container.addView(createFieldText("Минимальное время пребывания", "${visitObject.dwellMinutes} мин"))
        container.addView(createFieldText("objectId", visitObject.objectId.toString()))
        container.addView(
            Button(this).apply {
                text = "Удалить место"
                setOnClickListener { confirmDeleteObject(visitObject) }
            }
        )

        return container
    }

    private fun showAddObjectDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }

        val addressInput = createDialogInput("Адрес", InputType.TYPE_CLASS_TEXT)
        val latitudeInput = createDialogInput(
            "Latitude, например 55.753930",
            InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
        )
        val longitudeInput = createDialogInput(
            "Longitude, например 37.620795",
            InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
        )
        val radiusInput = createDialogInput(
            "Радиус, м",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        ).apply {
            setText("180")
        }
        val dwellInput = createDialogInput(
            "Время пребывания, мин",
            InputType.TYPE_CLASS_NUMBER
        ).apply {
            setText("1")
        }

        content.addView(addressInput)
        content.addView(latitudeInput)
        content.addView(longitudeInput)
        content.addView(radiusInput)
        content.addView(dwellInput)

        AlertDialog.Builder(this)
            .setTitle("Новое место")
            .setView(content)
            .setPositiveButton("Добавить") { _, _ ->
                addObjectFromInputs(
                    address = addressInput.text.toString(),
                    latitude = latitudeInput.text.toString(),
                    longitude = longitudeInput.text.toString(),
                    radius = radiusInput.text.toString(),
                    dwell = dwellInput.text.toString()
                )
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun createDialogInput(hintText: String, inputTypeValue: Int): EditText {
        return EditText(this).apply {
            hint = hintText
            inputType = inputTypeValue
            setSingleLine(true)
        }
    }

    private fun addObjectFromInputs(
        address: String,
        latitude: String,
        longitude: String,
        radius: String,
        dwell: String
    ) {
        val cleanAddress = address.trim()
        val latitudeValue = parseDecimal(latitude)
        val longitudeValue = parseDecimal(longitude)
        val radiusValue = parseDecimal(radius)?.toFloat()
        val dwellValue = dwell.trim().toIntOrNull()

        when {
            cleanAddress.isBlank() -> {
                showToast("Введите адрес")
                return
            }
            latitudeValue == null || latitudeValue !in -90.0..90.0 -> {
                showToast("Некорректная latitude")
                return
            }
            longitudeValue == null || longitudeValue !in -180.0..180.0 -> {
                showToast("Некорректная longitude")
                return
            }
            radiusValue == null || radiusValue <= 0f -> {
                showToast("Некорректный радиус")
                return
            }
            dwellValue == null || dwellValue <= 0 -> {
                showToast("Некорректное время пребывания")
                return
            }
        }

        val visitObject = VisitRepository.addObject(
            address = cleanAddress,
            latitude = latitudeValue,
            longitude = longitudeValue,
            radiusMeters = radiusValue,
            dwellMinutes = dwellValue
        )
        renderObjects()
        ensureGeofencesRegistered(requestPermissionsIfMissing = true)
        showToast("Место добавлено: objectId=${visitObject.objectId}")
    }

    private fun confirmDeleteObject(visitObject: VisitObject) {
        AlertDialog.Builder(this)
            .setTitle("Удалить место?")
            .setMessage("objectId=${visitObject.objectId}\n${visitObject.address}")
            .setPositiveButton("Удалить") { _, _ ->
                if (VisitRepository.removeObject(visitObject.objectId)) {
                    renderObjects()
                    ensureGeofencesRegistered(requestPermissionsIfMissing = true)
                    showToast("Место удалено")
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun parseDecimal(value: String): Double? {
        return value.trim().replace(',', '.').toDoubleOrNull()
    }

    private fun showSyncUrlDialog() {
        val input = createDialogInput(
            "https://example.com/api/visit-events",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        ).apply {
            setText(SyncSettingsRepository.getSyncUrl())
        }

        AlertDialog.Builder(this)
            .setTitle("URL синхронизации")
            .setMessage("Оставьте пустым для mock-синхронизации без сервера.")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                SyncSettingsRepository.saveSyncUrl(input.text.toString())
                syncStatusText.text = buildSyncStatusText()
                showToast("URL синхронизации сохранен")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun syncEvents() {
        syncStatusText.text = "Синхронизация..."
        val endpointUrl = SyncSettingsRepository.getSyncUrl()
        eventSyncManager.syncPendingEvents(endpointUrl = endpointUrl) { result ->
            runOnUiThread {
                syncStatusText.text = buildSyncStatusText()
                renderObjects()
                showToast("${result.message}: ${result.syncedCount}")
            }
        }
    }

    private fun autoSyncEvents() {
        if (VisitRepository.getPendingEventCount() == 0) return

        val endpointUrl = SyncSettingsRepository.getSyncUrl()
        eventSyncManager.syncPendingEvents(
            endpointUrl = endpointUrl,
            allowMockSync = false
        ) { result ->
            Log.d(
                TAG,
                "Auto sync finished: success=${result.success}, " +
                    "synced=${result.syncedCount}, message=${result.message}"
            )
            runOnUiThread {
                if (::syncStatusText.isInitialized) {
                    syncStatusText.text = buildSyncStatusText()
                }
            }
        }
    }

    private fun buildSyncStatusText(): String {
        val pendingCount = VisitRepository.getPendingEventCount()
        val syncMode = if (SyncSettingsRepository.getSyncUrl().isBlank()) {
            "mock"
        } else {
            "server"
        }
        return "Событий к синхронизации: $pendingCount. Режим: $syncMode"
    }

    private fun createFieldText(label: String, value: String): TextView {
        return TextView(this).apply {
            text = "$label: $value"
            textSize = 14f
            setTextColor(Color.rgb(55, 55, 55))
            setPadding(0, dp(2), 0, dp(2))
        }
    }

    private fun ensureGeofencesRegistered(requestPermissionsIfMissing: Boolean) {
        if (!geofenceManager.hasRequiredPermissions()) {
            registrationStatusText.text = "Геозоны зарегистрируются автоматически после выдачи разрешений"
            if (requestPermissionsIfMissing) {
                requestRequiredPermissions()
            }
            return
        }

        val objects = VisitRepository.getObjects()
        val currentSignature = buildObjectsSignature(objects)
        if (currentSignature == lastRegisteredObjectsSignature) {
            return
        }

        registrationStatusText.text = "Автоматическая регистрация геозон..."
        geofenceManager.registerGeofences(
            visitObjects = objects,
            onSuccess = {
                lastRegisteredObjectsSignature = currentSignature
                registrationStatusText.text = "Геозоны зарегистрированы автоматически: ${objects.size}"
            },
            onError = { exception ->
                registrationStatusText.text = "Ошибка автоматической регистрации: ${exception.message}"
            }
        )
    }

    private fun buildObjectsSignature(objects: List<VisitObject>): String {
        return objects.joinToString(separator = "|") { visitObject ->
            "${visitObject.objectId}," +
                "${visitObject.latitude}," +
                "${visitObject.longitude}," +
                "${visitObject.radiusMeters}," +
                visitObject.dwellMinutes
        }
    }

    private fun requestRequiredPermissions() {
        if (!hasFineLocationPermission()) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FINE_LOCATION
            )
            return
        }

        requestBackgroundLocationIfNeeded()
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasBackgroundLocationPermission()) {
            return
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                REQUEST_BACKGROUND_LOCATION
            )
        } else {
            showBackgroundLocationSettingsDialog()
        }
    }

    private fun showBackgroundLocationSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Фоновая геолокация")
            .setMessage(
                "Для работы геозон в фоне откройте настройки приложения и разрешите " +
                    "доступ к местоположению в режиме \"Всегда\"."
            )
            .setPositiveButton("Открыть настройки") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .setNegativeButton("Позже", null)
            .show()
    }

    private fun hasFineLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun statusText(status: VisitStatus): String {
        return when (status) {
            VisitStatus.NOT_VISITED -> "NOT_VISITED"
            VisitStatus.IN_ZONE -> "IN_ZONE"
            VisitStatus.VISITED -> "VISITED"
            VisitStatus.LEFT_AFTER_VISIT -> "LEFT_AFTER_VISIT"
        }
    }

    private fun showToast(message: String) {
        Log.d(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_FINE_LOCATION = 2001
        private const val REQUEST_BACKGROUND_LOCATION = 2002
    }
}
