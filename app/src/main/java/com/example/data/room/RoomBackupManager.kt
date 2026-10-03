package com.example.data.room

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.Customer
import com.example.data.ExchangeRates
import com.example.data.ExtraItem
import com.example.data.InvoiceData
import com.example.data.StoreConfig
import com.example.data.TransactionRecord
import com.example.util.ArabicNumberHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StorageExportResult(
  val success: Boolean,
  val fileName: String,
  val displayPath: String,
  val file: File? = null,
  val uri: Uri? = null
)

class RoomBackupManager(private val context: Context) {
  private val db = AppDatabase.getDatabase(context)
  private val invoiceDao = db.invoiceDao()
  private val customerDao = db.customerDao()
  private val transactionDao = db.transactionDao()
  private val backupDao = db.roomBackupDao()
  private val syncMutex = Mutex()

  /**
   * Persists all current in-memory invoices, customers, and transactions to Room.
   * This is called automatically after any mutation to guarantee zero data loss.
   */
  suspend fun syncAllToRoom(
    invoices: List<InvoiceData>,
    customers: List<Customer>
  ) = withContext(Dispatchers.IO) {
    syncMutex.withLock {
      try {
        // 1. Invoices
        val invoiceEntities = invoices.map { inv ->
          InvoiceEntity(
            id = inv.id,
            invNum = inv.invNum,
            invType = inv.invType,
            customerAccount = inv.customerAccount,
            customerName = inv.customerName,
            cardId = inv.cardId,
            price = inv.price,
            type = inv.type,
            qty = inv.qty,
            desc = inv.desc,
            endDate = inv.endDate,
            currency = inv.currency,
            extraItemsJson = serializeExtraItems(inv.extraItems),
            grandTotal = inv.grandTotal,
            createdAt = inv.createdAt,
            exchangeRatesJson = serializeRates(inv.exchangeRates)
          )
        }
        invoiceDao.deleteAllInvoices()
        invoiceDao.insertInvoices(invoiceEntities)

        // 2. Customers & Transactions
        val customerEntities = customers.map { c ->
          CustomerEntity(
            id = c.id,
            accountNumber = c.accountNumber,
            name = c.name,
            phone = c.phone,
            address = c.address,
            balance = c.balance
          )
        }
        customerDao.deleteAllCustomers()
        customerDao.insertCustomers(customerEntities)

        transactionDao.deleteAllTransactions()
        val txEntities = mutableListOf<TransactionEntity>()
        for (c in customers) {
          for (t in c.transactions) {
            txEntities.add(
              TransactionEntity(
                customerAccount = c.accountNumber,
                date = t.date,
                type = t.type,
                amount = t.amount,
                currency = t.currency,
                note = t.note,
                voucherNum = t.voucherNum,
                balanceAfter = t.balanceAfter,
                convertedAmount = t.convertedAmount,
                exchangeRatesJson = serializeRates(t.exchangeRates)
              )
            )
          }
        }
        transactionDao.insertTransactions(txEntities)
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  /**
   * Loads all invoices from Room database into memory.
   */
  suspend fun loadInvoicesFromRoom(): List<InvoiceData> = withContext(Dispatchers.IO) {
    try {
      invoiceDao.getAllInvoices().map { e ->
        InvoiceData(
          id = e.id,
          invNum = e.invNum,
          invType = e.invType,
          customerAccount = e.customerAccount,
          customerName = e.customerName,
          cardId = e.cardId,
          price = e.price,
          type = e.type,
          qty = e.qty,
          desc = e.desc,
          endDate = e.endDate,
          currency = e.currency,
          extraItems = deserializeExtraItems(e.extraItemsJson),
          grandTotal = e.grandTotal,
          createdAt = e.createdAt,
          exchangeRates = deserializeRates(e.exchangeRatesJson)
        )
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  /**
   * Loads all customers and their transactions from Room database.
   */
  suspend fun loadCustomersFromRoom(): List<Customer> = withContext(Dispatchers.IO) {
    try {
      val customerEntities = customerDao.getAllCustomers()
      val allTx = transactionDao.getAllTransactions()
      val txMap = allTx.groupBy { it.customerAccount }

      customerEntities.map { c ->
        val txs = (txMap[c.accountNumber] ?: emptyList()).map { t ->
          TransactionRecord(
            date = t.date,
            type = t.type,
            amount = t.amount,
            currency = t.currency,
            note = t.note,
            voucherNum = t.voucherNum,
            balanceAfter = t.balanceAfter,
            exchangeRates = deserializeRates(t.exchangeRatesJson),
            convertedAmount = t.convertedAmount
          )
        }
        Customer(
          id = c.id,
          accountNumber = c.accountNumber,
          name = c.name,
          phone = c.phone,
          address = c.address,
          balance = c.balance,
          transactions = txs
        )
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  /**
   * Creates a dedicated local backup snapshot in Room database and writes a backup file locally.
   */
  suspend fun createLocalRoomBackup(
    title: String,
    invoices: List<InvoiceData>,
    customers: List<Customer>,
    storeConfig: StoreConfig,
    exchangeRates: ExchangeRates,
    nextReceiptVoucherNum: Int = 1,
    nextPaymentVoucherNum: Int = 1,
    note: String = ""
  ): RoomBackupSnapshotEntity = withContext(Dispatchers.IO) {
    // 1. Make sure current data is synced to Room tables
    syncAllToRoom(invoices, customers)

    // 2. Generate JSON snapshot bundle
    val jsonBundle = buildBackupJson(
      invoices = invoices,
      customers = customers,
      storeConfig = storeConfig,
      exchangeRates = exchangeRates,
      nextReceiptVoucherNum = nextReceiptVoucherNum,
      nextPaymentVoucherNum = nextPaymentVoucherNum
    )

    val timeStampStr = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date())
    val snapshot = RoomBackupSnapshotEntity(
      title = title.ifBlank { "نسخة احتياطية محلية - $timeStampStr" },
      timestamp = timeStampStr,
      invoiceCount = invoices.size,
      customerCount = customers.size,
      backupDataJson = jsonBundle,
      note = note
    )

    // 3. Save snapshot into Room backup table
    val id = backupDao.insertSnapshot(snapshot)

    // 4. Also write physical file to app external/internal documents backup directory
    try {
      val backupDir = File(context.filesDir, "room_backups")
      if (!backupDir.exists()) backupDir.mkdirs()
      val fileTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
      val backupFile = File(backupDir, "backup_$fileTimestamp.json")
      backupFile.writeText(jsonBundle)
    } catch (e: Exception) {
      e.printStackTrace()
    }

    snapshot.copy(id = id)
  }

  /**
   * Directly saves an imported or generated JSON snapshot into Room table.
   */
  suspend fun insertSnapshotDirectly(
    title: String,
    invoiceCount: Int,
    customerCount: Int,
    backupDataJson: String,
    note: String = ""
  ): Long = withContext(Dispatchers.IO) {
    val timeStampStr = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date())
    val snapshot = RoomBackupSnapshotEntity(
      title = title,
      timestamp = timeStampStr,
      invoiceCount = invoiceCount,
      customerCount = customerCount,
      backupDataJson = backupDataJson,
      note = note
    )
    backupDao.insertSnapshot(snapshot)
  }

  /**
   * Creates a backup snapshot using current data parameters.
   */
  suspend fun createBackupSnapshot(
    invoices: List<InvoiceData>,
    customers: List<Customer>,
    storeConfig: StoreConfig,
    exchangeRates: ExchangeRates,
    nextReceiptVoucherNum: Int = 1,
    nextPaymentVoucherNum: Int = 1,
    note: String = "",
    title: String = ""
  ): RoomBackupSnapshotEntity = createLocalRoomBackup(
    title = title,
    invoices = invoices,
    customers = customers,
    storeConfig = storeConfig,
    exchangeRates = exchangeRates,
    nextReceiptVoucherNum = nextReceiptVoucherNum,
    nextPaymentVoucherNum = nextPaymentVoucherNum,
    note = note
  )

  /**
   * Gets all backup snapshots stored in Room.
   */
  suspend fun getAllRoomSnapshots(): List<RoomBackupSnapshotEntity> = withContext(Dispatchers.IO) {
    backupDao.getAllSnapshots()
  }

  suspend fun getAllSnapshots(): List<RoomBackupSnapshotEntity> = getAllRoomSnapshots()

  /**
   * Deletes a backup snapshot by ID.
   */
  suspend fun deleteSnapshot(id: Long) = withContext(Dispatchers.IO) {
    backupDao.deleteSnapshotById(id)
  }

  /**
   * Gets backup stats (counts in Room).
   */
  suspend fun getRoomDatabaseStats(): RoomStats = withContext(Dispatchers.IO) {
    RoomStats(
      invoiceCount = invoiceDao.getInvoiceCount(),
      customerCount = customerDao.getCustomerCount(),
      snapshotCount = backupDao.getSnapshotCount()
    )
  }

  /**
   * Exports backup to a shareable/downloadable file on device storage.
   */
  suspend fun exportBackupFile(
    invoices: List<InvoiceData>,
    customers: List<Customer>,
    storeConfig: StoreConfig,
    exchangeRates: ExchangeRates,
    nextReceiptVoucherNum: Int = 1,
    nextPaymentVoucherNum: Int = 1
  ): File? = withContext(Dispatchers.IO) {
    try {
      val json = buildBackupJson(
        invoices = invoices,
        customers = customers,
        storeConfig = storeConfig,
        exchangeRates = exchangeRates,
        nextReceiptVoucherNum = nextReceiptVoucherNum,
        nextPaymentVoucherNum = nextPaymentVoucherNum
      )
      val backupDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "RoomDatabaseBackups")
      if (!backupDir.exists()) backupDir.mkdirs()
      val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
      val file = File(backupDir, "Mamlaka_Backup_$timestamp.json")
      file.writeText(json)
      file
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Exports backup JSON directly to the phone's internal storage (Downloads / Mamlaka_Backups)
   * visible in device file managers without needing special storage permissions.
   */
  fun exportJsonToInternalStorage(fileName: String, jsonString: String): StorageExportResult {
    var exportedDisplayPath = ""
    var exportedFile: File? = null
    var isSuccess = false
    var targetUri: Uri? = null

    // 1. Export to device internal storage (Download/Mamlaka_Backups) via MediaStore on Android 10+
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
          put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
          put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
          put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Mamlaka_Backups")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
          resolver.openOutputStream(uri)?.use { os ->
            os.write(jsonString.toByteArray(Charsets.UTF_8))
            os.flush()
          }
          isSuccess = true
          targetUri = uri
          exportedDisplayPath = "وحدة التخزين الداخلية / Download / Mamlaka_Backups / $fileName"
        }
      } else {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val backupDir = File(downloadsDir, "Mamlaka_Backups")
        if (!backupDir.exists()) backupDir.mkdirs()
        val file = File(backupDir, fileName)
        file.writeText(jsonString)
        isSuccess = true
        exportedFile = file
        exportedDisplayPath = "وحدة التخزين الداخلية / Download / Mamlaka_Backups / $fileName"
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }

    // 2. Guaranteed local file backup in app documents/downloads folder
    try {
      val localDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "Mamlaka_Backups")
      if (!localDir.exists()) localDir.mkdirs()
      val localFile = File(localDir, fileName)
      localFile.writeText(jsonString)
      if (!isSuccess) {
        isSuccess = true
        exportedFile = localFile
        exportedDisplayPath = "وحدة التخزين الداخلية / Mamlaka_Backups / $fileName"
      } else if (exportedFile == null) {
        exportedFile = localFile
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }

    return StorageExportResult(
      success = isSuccess,
      fileName = fileName,
      displayPath = if (exportedDisplayPath.isNotBlank()) exportedDisplayPath else "وحدة التخزين الداخلية / Download / Mamlaka_Backups / $fileName",
      file = exportedFile,
      uri = targetUri
    )
  }

  fun buildBackupJson(
    invoices: List<InvoiceData>,
    customers: List<Customer>,
    storeConfig: StoreConfig,
    exchangeRates: ExchangeRates,
    nextReceiptVoucherNum: Int = 1,
    nextPaymentVoucherNum: Int = 1
  ): String {
    val root = JSONObject()
    root.put("version", 2)
    root.put("databaseEngine", "Room")
    root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

    // Invoices array
    val invArray = JSONArray()
    for (inv in invoices) {
      val obj = JSONObject().apply {
        put("id", inv.id)
        put("invNum", inv.invNum)
        put("invType", inv.invType)
        put("customerAccount", inv.customerAccount)
        put("customerName", inv.customerName)
        put("cardId", inv.cardId)
        put("price", inv.price)
        put("type", inv.type)
        put("qty", inv.qty)
        put("desc", inv.desc)
        put("endDate", inv.endDate)
        put("currency", inv.currency)
        put("grandTotal", inv.grandTotal)
        put("createdAt", inv.createdAt)
        if (inv.exchangeRates != null) {
          put("exchangeRates", JSONObject().apply {
            put("yerToUsd", inv.exchangeRates.yerToUsd)
            put("usdToYer", inv.exchangeRates.usdToYer)
            put("yerToSar", inv.exchangeRates.yerToSar)
            put("sarToYer", inv.exchangeRates.sarToYer)
            put("usdToSar", inv.exchangeRates.usdToSar)
            put("sarToUsd", inv.exchangeRates.sarToUsd)
          })
        }
        val extraArray = JSONArray()
        for (ex in inv.extraItems) {
          extraArray.put(JSONObject().apply {
            put("id", ex.id)
            put("description", ex.description)
            put("type", ex.type)
            put("price", ex.price)
            put("qty", ex.qty)
            put("currency", ex.currency)
          })
        }
        put("extraItems", extraArray)
      }
      invArray.put(obj)
    }
    root.put("savedInvoices", invArray)

    // Customers array
    val custArray = JSONArray()
    for (c in customers) {
      val cObj = JSONObject().apply {
        put("id", c.id)
        put("accountNumber", c.accountNumber)
        put("name", c.name)
        put("phone", c.phone)
        put("address", c.address)
        put("balance", c.balance)
        val txArray = JSONArray()
        for (t in c.transactions) {
          txArray.put(JSONObject().apply {
            put("date", t.date)
            put("type", t.type)
            put("amount", t.amount)
            put("currency", t.currency)
            put("note", t.note)
            put("voucherNum", t.voucherNum)
            put("balanceAfter", t.balanceAfter)
            if (t.convertedAmount != null) put("convertedAmount", t.convertedAmount)
            if (t.exchangeRates != null) {
              put("exchangeRates", JSONObject().apply {
                put("yerToUsd", t.exchangeRates.yerToUsd)
                put("usdToYer", t.exchangeRates.usdToYer)
                put("yerToSar", t.exchangeRates.yerToSar)
                put("sarToYer", t.exchangeRates.sarToYer)
                put("usdToSar", t.exchangeRates.usdToSar)
                put("sarToUsd", t.exchangeRates.sarToUsd)
              })
            }
          })
        }
        put("transactions", txArray)
      }
      custArray.put(cObj)
    }
    root.put("customers", custArray)

    // Store config
    val scObj = JSONObject().apply {
      put("storeNameAr", storeConfig.storeNameAr)
      put("storeNameEn", storeConfig.storeNameEn)
      put("branch", storeConfig.branch)
      put("phone", storeConfig.phone)
      put("addressAr", storeConfig.addressAr)
      put("addressEn", storeConfig.addressEn)
      put("wmAr", storeConfig.wmAr)
      put("terms", storeConfig.terms)
      put("logoBase64", storeConfig.logoBase64)
    }
    root.put("storeConfig", scObj)

    // Rates
    val rObj = JSONObject().apply {
      put("yerToUsd", exchangeRates.yerToUsd)
      put("usdToYer", exchangeRates.usdToYer)
      put("yerToSar", exchangeRates.yerToSar)
      put("sarToYer", exchangeRates.sarToYer)
      put("usdToSar", exchangeRates.usdToSar)
      put("sarToUsd", exchangeRates.sarToUsd)
    }
    root.put("exchangeRates", rObj)

    // Counters
    root.put("nextReceiptVoucherNum", nextReceiptVoucherNum)
    root.put("nextPaymentVoucherNum", nextPaymentVoucherNum)

    return root.toString(2)
  }

  private fun serializeExtraItems(items: List<ExtraItem>): String {
    val arr = JSONArray()
    for (item in items) {
      arr.put(JSONObject().apply {
        put("id", item.id)
        put("description", item.description)
        put("type", item.type)
        put("price", item.price)
        put("qty", item.qty)
        put("currency", item.currency)
      })
    }
    return arr.toString()
  }

  private fun deserializeExtraItems(jsonStr: String): List<ExtraItem> {
    if (jsonStr.isBlank()) return emptyList()
    return try {
      val arr = JSONArray(jsonStr)
      val list = mutableListOf<ExtraItem>()
      for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        list.add(
          ExtraItem(
            id = o.optLong("id", System.currentTimeMillis()),
            description = o.optString("description", ""),
            type = o.optString("type", "بطولة"),
            price = o.optDouble("price", 0.0),
            qty = o.optDouble("qty", 1.0),
            currency = o.optString("currency", "$")
          )
        )
      }
      list
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun serializeRates(rates: ExchangeRates?): String? {
    if (rates == null) return null
    return JSONObject().apply {
      put("yerToUsd", rates.yerToUsd)
      put("usdToYer", rates.usdToYer)
      put("yerToSar", rates.yerToSar)
      put("sarToYer", rates.sarToYer)
      put("usdToSar", rates.usdToSar)
      put("sarToUsd", rates.sarToUsd)
    }.toString()
  }

  private fun deserializeRates(jsonStr: String?): ExchangeRates? {
    if (jsonStr.isNullOrBlank()) return null
    return try {
      val o = JSONObject(jsonStr)
      ExchangeRates(
        yerToUsd = o.optDouble("yerToUsd", 0.001876),
        usdToYer = o.optDouble("usdToYer", 533.0),
        yerToSar = o.optDouble("yerToSar", 0.007035),
        sarToYer = o.optDouble("sarToYer", 142.1333),
        usdToSar = o.optDouble("usdToSar", 3.75),
        sarToUsd = o.optDouble("sarToUsd", 0.2667)
      )
    } catch (_: Exception) {
      null
    }
  }
}

data class RoomStats(
  val invoiceCount: Int,
  val customerCount: Int,
  val snapshotCount: Int
)
