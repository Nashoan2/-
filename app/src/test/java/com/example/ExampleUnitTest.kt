package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCustomerMultiCurrencyReconciliation_UsdInvoiceAndYerReceiptVoucher() {
    val rates = com.example.data.ExchangeRates(
      usdToYer = 533.0,
      yerToUsd = 1.0 / 533.0
    )

    // Invoice of 100 USD (عليكم)
    val invoiceAmountUsd = 100.0
    val invoiceCurrency = "$"

    // Receipt voucher of 53,300 YER (لكم)
    val voucherAmountYer = 53300.0
    val voucherCurrency = "YER"

    // Base currency of customer is USD
    val baseCurrency = "$"

    val convertedDebit = com.example.util.ArabicNumberHelper.convertCurrency(
      invoiceAmountUsd,
      invoiceCurrency,
      baseCurrency,
      rates
    )
    val convertedCredit = com.example.util.ArabicNumberHelper.convertCurrency(
      voucherAmountYer,
      voucherCurrency,
      baseCurrency,
      rates
    )

    assertEquals(100.0, convertedDebit, 0.001)
    assertEquals(100.0, convertedCredit, 0.001)

    val netBalance = convertedDebit - convertedCredit
    val finalBalance = if (Math.abs(netBalance) < 0.005) 0.0 else netBalance

    assertEquals(0.0, finalBalance, 0.0)

    val finalBalTitle = if (finalBalance > 0.005) {
      "الباقي عليكم"
    } else if (finalBalance < -0.005) {
      "الباقي لكم"
    } else {
      "الباقي (لكم / عليكم)"
    }
    assertEquals("الباقي (لكم / عليكم)", finalBalTitle)
  }

  @Test
  fun testCustomerBalancePersistenceCalculation() {
    val rates = com.example.data.ExchangeRates(
      usdToYer = 533.0,
      yerToUsd = 1.0 / 533.0
    )
    val txList = listOf(
      com.example.data.TransactionRecord(
        date = "2026-10-01 10:00",
        type = "فاتورة",
        amount = 150.0,
        currency = "$",
        note = "فاتورة مبيعات",
        balanceAfter = 150.0
      ),
      com.example.data.TransactionRecord(
        date = "2026-10-01 11:00",
        type = "قبض",
        amount = 50.0,
        currency = "$",
        note = "سند قبض نقدي",
        balanceAfter = 100.0
      )
    )
    val customer = com.example.data.Customer(
      id = 1L,
      accountNumber = "105",
      name = "سعيد أحمد",
      phone = "777000111",
      address = "صنعاء",
      balance = 0.0,
      transactions = txList
    )

    // Calculate customer balance
    val baseCurrency = customer.transactions.firstOrNull { it.currency.isNotBlank() }?.currency ?: "$"
    var currentBalanceInBase = 0.0
    val updatedTransactions = customer.transactions.map { t ->
      val tCurrency = if (t.currency.isNotBlank()) t.currency else baseCurrency
      val convertedAmount = com.example.util.ArabicNumberHelper.convertCurrency(t.amount, tCurrency, baseCurrency, rates)
      when (t.type) {
        "قبض" -> currentBalanceInBase -= convertedAmount
        "صرف", "فاتورة" -> currentBalanceInBase += convertedAmount
        else -> currentBalanceInBase += convertedAmount
      }
      t.copy(balanceAfter = currentBalanceInBase)
    }
    val finalCustomer = customer.copy(balance = currentBalanceInBase, transactions = updatedTransactions)

    assertEquals(100.0, finalCustomer.balance, 0.001)
    assertEquals(150.0, finalCustomer.transactions[0].balanceAfter, 0.001)
    assertEquals(100.0, finalCustomer.transactions[1].balanceAfter, 0.001)
  }

  @Test
  fun testHistoricalTransactionsUnaffectedByNewExchangeRates() {
    val initialRates = com.example.data.ExchangeRates(
      sarToYer = 140.0,
      yerToSar = 1.0 / 140.0
    )
    val baseCurrency = "YER"

    // 1. Transaction saved with initial rates (100 SAR = 14,000 YER)
    val converted1 = com.example.util.ArabicNumberHelper.convertCurrency(100.0, "SAR", baseCurrency, initialRates)
    assertEquals(14000.0, converted1, 0.001)

    val tx1 = com.example.data.TransactionRecord(
      date = "2026-10-01 10:00",
      type = "قبض",
      amount = 100.0,
      currency = "SAR",
      note = "سند قبض أولي",
      voucherNum = "1",
      balanceAfter = -14000.0,
      exchangeRates = initialRates,
      convertedAmount = converted1
    )

    // 2. Later, exchange rate changes to 1 SAR = 160 YER
    val newRates = com.example.data.ExchangeRates(
      sarToYer = 160.0,
      yerToSar = 1.0 / 160.0
    )

    // Verify that tx1 still uses its locked convertedAmount / saved rates
    val tx1EffectiveConverted = tx1.convertedAmount ?: com.example.util.ArabicNumberHelper.convertCurrency(
      tx1.amount, tx1.currency, baseCurrency, tx1.exchangeRates ?: newRates
    )
    assertEquals(14000.0, tx1EffectiveConverted, 0.001)

    // 3. New transaction recorded after rate change (100 SAR = 16,000 YER)
    val converted2 = com.example.util.ArabicNumberHelper.convertCurrency(100.0, "SAR", baseCurrency, newRates)
    assertEquals(16000.0, converted2, 0.001)

    val tx2 = com.example.data.TransactionRecord(
      date = "2026-10-03 10:00",
      type = "قبض",
      amount = 100.0,
      currency = "SAR",
      note = "سند قبض جديد بسعر الصرف الحالي",
      voucherNum = "2",
      balanceAfter = -30000.0,
      exchangeRates = newRates,
      convertedAmount = converted2
    )

    val tx2EffectiveConverted = tx2.convertedAmount ?: com.example.util.ArabicNumberHelper.convertCurrency(
      tx2.amount, tx2.currency, baseCurrency, tx2.exchangeRates ?: newRates
    )
    assertEquals(16000.0, tx2EffectiveConverted, 0.001)

    // Combined balance reflects 14,000 + 16,000 = 30,000 YER (not 32,000 YER)
    val totalPaid = tx1EffectiveConverted + tx2EffectiveConverted
    assertEquals(30000.0, totalPaid, 0.001)

    // 4. Editing tx1 preserves its historical exchange rate
    val editedAmount = 150.0
    val editedConverted = com.example.util.ArabicNumberHelper.convertCurrency(
      editedAmount, tx1.currency, baseCurrency, tx1.exchangeRates ?: newRates
    )
    // 150 * 140 = 21,000 YER (not 150 * 160 = 24,000)
    assertEquals(21000.0, editedConverted, 0.001)
  }

  @Test
  fun testCustomerStatementSeptemberToOctober() {
    val dateFormats = listOf(
      "15/09/2026",
      "15/9/2026",
      "15/09/2026 14:30",
      "15/9/2026 2:30 م",
      "15/09/2026 02:30 م",
      "15/09/2026 02:30 ص",
      "15-09-2026",
      "15-9-2026",
      "2026-09-15",
      "2026/09/15",
      "2026/9/15"
    )
    for (dStr in dateFormats) {
      val parsed = com.example.util.ArabicNumberHelper.parseDate(dStr)
      assertNotNull("Failed to parse date: $dStr", parsed)
    }

    val startDateStr = "01/10/2026"
    val endDateStr = "03/10/2026"
    val sDate = com.example.util.ArabicNumberHelper.parseDate(startDateStr)
    val eDate = com.example.util.ArabicNumberHelper.parseDate(endDateStr)
    assertNotNull(sDate)
    assertNotNull(eDate)

    val startCal = java.util.Calendar.getInstance().apply {
      time = sDate!!
      set(java.util.Calendar.HOUR_OF_DAY, 0)
      set(java.util.Calendar.MINUTE, 0)
      set(java.util.Calendar.SECOND, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val endCal = java.util.Calendar.getInstance().apply {
      time = eDate!!
      set(java.util.Calendar.HOUR_OF_DAY, 23)
      set(java.util.Calendar.MINUTE, 59)
      set(java.util.Calendar.SECOND, 59)
      set(java.util.Calendar.MILLISECOND, 999)
    }

    val septDate = com.example.util.ArabicNumberHelper.parseDate("15/09/2026 10:00")!!
    assertTrue("Sept date must be before startCal", septDate.before(startCal.time))
  }
}
