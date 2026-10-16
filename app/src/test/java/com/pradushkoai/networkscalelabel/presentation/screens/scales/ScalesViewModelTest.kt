package com.pradushkoai.networkscalelabel.presentation.scales

import com.pradushkoai.networkscalelabel.domain.model.Ip4Network
import com.pradushkoai.networkscalelabel.domain.model.PhoneNetwork
import com.pradushkoai.networkscalelabel.domain.model.PhoneNetworks
import com.pradushkoai.networkscalelabel.domain.model.PluRecord
import com.pradushkoai.networkscalelabel.domain.model.ScanResult
import com.pradushkoai.networkscalelabel.domain.model.ScaleCommunicationException
import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.model.ScaleErrorKind
import com.pradushkoai.networkscalelabel.domain.model.ScaleHost
import com.pradushkoai.networkscalelabel.domain.model.ScaleReadEvent
import com.pradushkoai.networkscalelabel.domain.model.ScaleStatus
import com.pradushkoai.networkscalelabel.domain.repository.ConnectionSettingsRepository
import com.pradushkoai.networkscalelabel.domain.repository.NetworkRepository
import com.pradushkoai.networkscalelabel.domain.usecase.ConnectScalesUseCase
import com.pradushkoai.networkscalelabel.domain.usecase.ReadPluDatabaseUseCase
import com.pradushkoai.networkscalelabel.domain.usecase.ScanScalesUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScalesViewModelTest {

    private val testConfig = ScaleConnectionConfig("192.168.140.89", 1111, "0030")

    private val testStatus = ScaleStatus(
        firmwareVersion = "4.5",
        construction = 2,
        firmwareDate = "07.11.25",
        goodsTableSize = 3000,
        messagesTableSize = 100,
        linesPerMessage = 4,
        maxWeightKg = 15,
        scaleNumber = 17,
        currentDate = "01.01.26",
        currentTime = "12.00.00",
        mode = 0,
        isQuickLoadMode = false,
    )

    /** Телефон в сети 192.168.1.0/24 — весы 192.168.140.89 в другой подсети. */
    private val foreignPhoneNetworks = PhoneNetworks(
        listOf(
            PhoneNetwork(
                interfaceName = "wlan0",
                displayName = "Wi-Fi",
                address = 0xC0A80165L, // 192.168.1.101
                network = Ip4Network.of(0xC0A80165L, 24),
            ),
        ),
    )

    /** Телефон в одной подсети с весами. */
    private val samePhoneNetworks = PhoneNetworks(
        listOf(
            PhoneNetwork(
                interfaceName = "wlan0",
                displayName = "Wi-Fi",
                address = 0xC0A88C17L, // 192.168.140.23
                network = Ip4Network.of(0xC0A88C17L, 24),
            ),
        ),
    )

    private fun testPlu(number: Int) = PluRecord(
        number = number,
        code = 200_000L + number,
        name1 = "Товар $number",
        name2 = "",
        priceMinorUnits = 10_000L,
        shelfLifeDays = 5,
        tareGrams = 0,
        groupCode = 0,
        messageNumber = 0,
        images = 0,
        rostest = "",
        isPiece = false,
        labelFormat = 1,
        barcodeStructure = 1,
        realizationDate = null,
        manufactureDate = null,
        extended = true,
    )

    private lateinit var connectUseCase: ConnectScalesUseCase
    private lateinit var readUseCase: ReadPluDatabaseUseCase
    private lateinit var scanUseCase: ScanScalesUseCase
    private lateinit var settings: ConnectionSettingsRepository
    private lateinit var networkRepository: NetworkRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        connectUseCase = mockk()
        readUseCase = mockk()
        scanUseCase = mockk()
        settings = mockk(relaxed = true)
        networkRepository = mockk()
        every { settings.load() } returns testConfig
        every { networkRepository.refresh() } returns samePhoneNetworks
        every { networkRepository.current() } returns samePhoneNetworks
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ScalesViewModel =
        ScalesViewModel(connectUseCase, readUseCase, scanUseCase, settings, networkRepository, testDispatcher)

    @Test
    fun `инициализация загружает сохранённые настройки и сеть телефона`() {
        val vm = createViewModel()
        assertEquals("192.168.140.89", vm.uiState.value.host)
        assertEquals("1111", vm.uiState.value.port)
        assertEquals("0030", vm.uiState.value.password)
        assertEquals(samePhoneNetworks, vm.uiState.value.phoneNetworks)
        assertFalse(vm.uiState.value.hostOutsideNetworks)
    }

    @Test
    fun `успешное подключение переводит состояние в Connected и сохраняет настройки`() {
        coEvery { connectUseCase(any()) } returns testStatus

        val vm = createViewModel()
        vm.connect()

        val connection = vm.uiState.value.connection
        assertTrue(connection is ConnectionState.Connected)
        assertEquals(3000, (connection as ConnectionState.Connected).status.goodsTableSize)
        verify(exactly = 1) { settings.save(any()) }
    }

    @Test
    fun `ошибка связи переводит состояние в Failed`() {
        coEvery { connectUseCase(any()) } throws ScaleCommunicationException("нет ответа")

        val vm = createViewModel()
        vm.connect()

        val connection = vm.uiState.value.connection
        assertTrue(connection is ConnectionState.Failed)
        // к таймауту добавляется подсказка, что проверить
        assertTrue((connection as ConnectionState.Failed).message.startsWith("нет ответа"))
        assertTrue(connection.message.contains("UDP-порт"))
    }

    @Test
    fun `нет маршрута к весам в чужой подсети даёт подсказку о подсетях`() {
        every { networkRepository.refresh() } returns foreignPhoneNetworks
        coEvery { connectUseCase(any()) } throws ScaleCommunicationException(
            "нет сетевого маршрута",
            kind = ScaleErrorKind.NO_ROUTE,
        )

        val vm = createViewModel()
        vm.connect()

        val message = (vm.uiState.value.connection as ConnectionState.Failed).message
        assertTrue(message.contains("нет сетевого маршрута"))
        assertTrue(message.contains("192.168.1.101"))
        assertTrue(message.contains("другой подсети"))
        assertTrue(vm.uiState.value.hostOutsideNetworks)
    }

    @Test
    fun `смена хоста пересчитывает признак чужой подсети`() {
        every { networkRepository.refresh() } returns foreignPhoneNetworks
        val vm = createViewModel()

        // сети подтянуты в init: весы 192.168.140.89 вне 192.168.1.0/24
        assertTrue(vm.uiState.value.hostOutsideNetworks)

        vm.onHostChange("192.168.1.50")
        assertFalse(vm.uiState.value.hostOutsideNetworks)

        vm.onHostChange("192.168.140.89")
        assertTrue(vm.uiState.value.hostOutsideNetworks)
    }

    @Test
    fun `поиск весов находит и подставляет единственные весы из чужой подсети`() {
        every { networkRepository.refresh() } returns foreignPhoneNetworks
        val found = listOf(ScaleHost("192.168.1.77", testStatus))
        coEvery { scanUseCase(any()) } returns ScanResult(foreignPhoneNetworks, found, 5_000)

        val vm = createViewModel()
        vm.scan()

        val phase = vm.uiState.value.scanPhase
        assertTrue(phase is ScanPhase.Done)
        assertEquals(1, (phase as ScanPhase.Done).hosts.size)
        // единственные найденные весы автоматически подставлены в поле адреса
        assertEquals("192.168.1.77", vm.uiState.value.host)
    }

    @Test
    fun `пустой результат поиска показывает сообщение и не трогает адрес`() {
        coEvery { scanUseCase(any()) } returns ScanResult(samePhoneNetworks, emptyList(), 3_000)

        val vm = createViewModel()
        vm.scan()

        val phase = vm.uiState.value.scanPhase
        assertTrue(phase is ScanPhase.Done)
        assertTrue((phase as ScanPhase.Done).hosts.isEmpty())
        assertEquals("192.168.140.89", vm.uiState.value.host)
    }

    @Test
    fun `чтение базы собирает события в итоговое состояние`() {
        val record = testPlu(1)
        every { readUseCase(any()) } returns flow {
            emit(ScaleReadEvent.Started(total = 3))
            emit(ScaleReadEvent.Progress(processed = 1, total = 3, found = 1, failed = 0))
            emit(ScaleReadEvent.Completed(listOf(record), failedCount = 0, durationMs = 5_000))
        }

        val vm = createViewModel()
        vm.startRead()

        val state = vm.uiState.value
        assertTrue(state.readPhase is ReadPhase.Done)
        assertEquals(1, (state.readPhase as ReadPhase.Done).count)
        assertEquals(1, state.records.size)
        assertEquals("Товар 1", state.records.first().displayName)
    }

    @Test
    fun `сбой чтения переводит фазу в Failed`() {
        every { readUseCase(any()) } returns flow {
            emit(ScaleReadEvent.Started(total = 3))
            throw ScaleCommunicationException("связь оборвалась")
        }

        val vm = createViewModel()
        vm.startRead()

        val phase = vm.uiState.value.readPhase
        assertTrue(phase is ReadPhase.Failed)
        assertEquals("связь оборвалась", (phase as ReadPhase.Failed).message)
    }

    @Test
    fun `поиск фильтрует по наименованию и коду`() {
        val records = listOf(testPlu(1), testPlu(2))
        val byName = filterRecords(records, "товар 2")
        assertEquals(1, byName.size)
        assertEquals(2, byName.first().number)

        val byCode = filterRecords(records, "200002")
        assertEquals(1, byCode.size)
        assertEquals(2, byCode.first().number)

        val byPluNumber = filterRecords(records, "1")
        assertEquals(1, byPluNumber.size)
    }
}
