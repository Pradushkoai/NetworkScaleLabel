package com.pradushkoai.networkscalelabel.presentation.scales

import com.pradushkoai.networkscalelabel.domain.model.PluRecord
import com.pradushkoai.networkscalelabel.domain.model.ScaleCommunicationException
import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.model.ScaleReadEvent
import com.pradushkoai.networkscalelabel.domain.model.ScaleStatus
import com.pradushkoai.networkscalelabel.domain.repository.ConnectionSettingsRepository
import com.pradushkoai.networkscalelabel.domain.usecase.ConnectScalesUseCase
import com.pradushkoai.networkscalelabel.domain.usecase.ReadPluDatabaseUseCase
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
    private lateinit var settings: ConnectionSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        connectUseCase = mockk()
        readUseCase = mockk()
        settings = mockk(relaxed = true)
        every { settings.load() } returns testConfig
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ScalesViewModel =
        ScalesViewModel(connectUseCase, readUseCase, settings)

    @Test
    fun `инициализация загружает сохранённые настройки`() {
        val vm = createViewModel()
        assertEquals("192.168.140.89", vm.uiState.value.host)
        assertEquals("1111", vm.uiState.value.port)
        assertEquals("0030", vm.uiState.value.password)
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
        assertEquals("нет ответа", (connection as ConnectionState.Failed).message)
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
