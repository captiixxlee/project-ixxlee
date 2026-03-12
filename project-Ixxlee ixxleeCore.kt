package com.ixxlee.core

// ---------------------------------------------------------
// Developer Mode
// ---------------------------------------------------------

object DevModeManager {
    private const val PASSCODE = "006660"

    fun isEnabled(prefs: android.content.SharedPreferences): Boolean =
        prefs.getBoolean("dev_mode_enabled", false)

    fun unlock(input: String, prefs: android.content.SharedPreferences): Boolean {
        val ok = input == PASSCODE
        if (ok) prefs.edit().putBoolean("dev_mode_enabled", true).apply()
        return ok
    }
}

// ---------------------------------------------------------
// User Events & UI Messages
// ---------------------------------------------------------

sealed class UserEvent {
    data class TextInput(val text: String) : UserEvent()
    data class VoiceInput(val transcript: String) : UserEvent()
    data class DeviceEvent(val deviceId: String, val payload: Map<String, Any?>) : UserEvent()
    data class SystemEvent(val type: String, val data: Map<String, Any?>) : UserEvent()
}

data class UiMessage(
    val text: String,
    val type: String = "text"
)

// ---------------------------------------------------------
// Orchestrator Core
// ---------------------------------------------------------

interface IxxleeOrchestrator {
    suspend fun handleUserEvent(event: UserEvent): OrchestratorResult
}

data class OrchestratorResult(
    val messages: List<UiMessage> = emptyList(),
    val deviceCommands: List<DeviceCommand> = emptyList(),
    val erpActions: List<ErpAction> = emptyList()
)

class DefaultIxxleeOrchestrator(
    private val memory: MemoryStore,
    private val plugins: List<IxxleePlugin>,
    private val erp: ErpEngine
) : IxxleeOrchestrator {

    override suspend fun handleUserEvent(event: UserEvent): OrchestratorResult {
        // 1. Fan-out to plugins
        val pluginResults = plugins.map { it.onEvent(event, memory) }

        // 2. Persist memory writes
        pluginResults.flatMap { it.memoryWrites }.forEach {
            memory.write(it.key, it.value, it.tags)
        }

        // 3. Build ERP context
        val erpContext = ErpContext(
            userId = "captain",
            recentEvents = listOf(event),
            memorySnapshot = memory.queryByTag("erp_relevant", 100)
        )

        // 4. ERP evaluation
        val erpActions = erp.evaluate(erpContext)

        // 5. Aggregate results
        return OrchestratorResult(
            messages = pluginResults.flatMap { it.messages },
            deviceCommands = pluginResults.flatMap { it.deviceCommands },
            erpActions = erpActions
        )
    }
}

// ---------------------------------------------------------
// Memory Backend (Key/Value + Vector)
// ---------------------------------------------------------

interface MemoryStore {
    suspend fun write(key: String, value: String, tags: List<String>)
    suspend fun read(key: String): String?
    suspend fun queryByTag(tag: String, limit: Int): List<MemoryRecord>
}

data class MemoryRecord(
    val key: String,
    val value: String,
    val tags: List<String>,
    val timestamp: Long
)

data class MemoryWrite(
    val key: String,
    val value: String,
    val tags: List<String>
)

interface VectorMemory {
    suspend fun embed(text: String): FloatArray
    suspend fun search(query: String, limit: Int): List<VectorResult>
}

data class VectorResult(
    val key: String,
    val score: Float
)

// ---------------------------------------------------------
// Plugin / Device Layer
// ---------------------------------------------------------

interface IxxleePlugin {
    val id: String
    val supportedEvents: List<String>

    suspend fun onEvent(
        event: UserEvent,
        memory: MemoryStore
    ): PluginResult
}

data class PluginResult(
    val deviceCommands: List<DeviceCommand> = emptyList(),
    val messages: List<UiMessage> = emptyList(),
    val memoryWrites: List<MemoryWrite> = emptyList()
)

data class DeviceCommand(
    val targetDeviceId: String,
    val command: String,
    val args: Map<String, Any?> = emptyMap()
)

// ---------------------------------------------------------
// Analyzer → Classifier → Plugin Generator Pipeline
// ---------------------------------------------------------

data class DeviceDescriptor(
    val raw: String,
    val metadata: Map<String, Any?> = emptyMap()
)

enum class DeviceType {
    UNKNOWN,
    USB_SENSOR,
    USB_STORAGE,
    CUSTOM_DEVICE
}

interface DescriptorAnalyzer {
    fun analyze(input: String): DeviceDescriptor
}

interface DeviceClassifier {
    fun classify(desc: DeviceDescriptor): DeviceType
}

interface PluginGenerator {
    fun generatePlugin(desc: DeviceDescriptor, type: DeviceType): IxxleePlugin
}

// ---------------------------------------------------------
// ERP Intelligence Layer
// ---------------------------------------------------------

interface ErpEngine {
    suspend fun evaluate(context: ErpContext): List<ErpAction>
}

data class ErpContext(
    val userId: String,
    val recentEvents: List<UserEvent>,
    val memorySnapshot: List<MemoryRecord>
)

data class ErpAction(
    val type: String,
    val payload: Map<String, Any?>
)

// ---------------------------------------------------------
// RLHF Feedback Loop
// ---------------------------------------------------------

data class Feedback(
    val event: UserEvent,
    val result: OrchestratorResult,
    val rating: Int,
    val notes: String?
)

// ---------------------------------------------------------
// Script Engine
// ---------------------------------------------------------

interface ScriptEngine {
    fun buildScript(commands: List<DeviceCommand>): String
    suspend fun execute(script: String): ExecutionResult
}

data class ExecutionResult(
    val success: Boolean,
    val output: String
)

// ---------------------------------------------------------
// USB Device Orchestration
// ---------------------------------------------------------

data class UsbDevice(
    val id: String,
    val vendorId: Int,
    val productId: Int,
    val name: String
)

interface UsbDeviceManager {
    fun listDevices(): List<UsbDevice>
    fun sendCommand(deviceId: String, command: String, args: Map<String, Any?>)
}