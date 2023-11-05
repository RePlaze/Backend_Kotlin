package mobi.sevenwinds.app.budget

import io.restassured.RestAssured
import mobi.sevenwinds.common.ServerTest
import mobi.sevenwinds.common.jsonBody
import mobi.sevenwinds.common.toResponse
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BudgetApiKtTest : ServerTest() {

    @BeforeEach
    internal fun setUp() {
        transaction { BudgetTable.deleteAll() }
    }

    @Test
    fun testBudgetPagination() {
        addRecord(BudgetRecord(2020, 5, 10, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 5, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 20, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 30, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 40, BudgetType.Приход))
        addRecord(BudgetRecord(2030, 1, 1, BudgetType.Расход))

        RestAssured.given()
            .queryParam("limit", 3)
            .queryParam("offset", 1)
            .get("/budget/year/2020/stats")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println("${response.total} / ${response.items} / ${response.totalByType}")

                assertEquals(5, response.total)
                assertEquals(3, response.items.size)
                assertEquals(105, response.totalByType[BudgetType.Приход.name])
            }
    }
    @Test
    fun testStatsSortOrder() {
        val records = listOf(
            BudgetRecord(2020, 5, 100, BudgetType.Приход),
            BudgetRecord(2020, 1, 5, BudgetType.Приход),
            BudgetRecord(2020, 5, 50, BudgetType.Приход),
            BudgetRecord(2020, 1, 30, BudgetType.Приход),
            BudgetRecord(2020, 5, 400, BudgetType.Приход)
        )

        val expectedOrder = records.sortedWith(
            compareBy({ it.year }, { -it.amount })
        )

        val response = RestAssured.given()
            .get("/budget/year/2020/stats?limit=100&offset=0")
            .toResponse<BudgetYearStatsResponse>()

        val sortedItems = response.items.sortedBy { it.month }
        val actualAmounts = sortedItems.map { it.amount }

        println(sortedItems)

        for (i in expectedOrder.indices) {
            assertEquals(expectedOrder[i].amount, actualAmounts[i])
        }
    }

    @Test
    fun testInvalidMonthValues() {
        RestAssured.given()
            .jsonBody(BudgetRecord(2020, -5, 5, BudgetType.Приход))
            .post("/budget/add")
            .then().statusCode(400)

        RestAssured.given()
            .jsonBody(BudgetRecord(2020, 15, 5, BudgetType.Приход))
            .post("/budget/add")
            .then().statusCode(400)
    }

    private fun addRecord(record: BudgetRecord) {
        RestAssured.given()
            .jsonBody(record.copy(authorId = null))
            .post("/budget/add")
            .toResponse<BudgetRecord>().let { response ->
                assertEquals(record, response)
            }
    }

}