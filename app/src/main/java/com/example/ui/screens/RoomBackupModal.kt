package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.viewmodel.InvoiceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupSavedSuccessInfo(
  val fileName: String,
  val displayPath: String,
  val invoiceCount: Int,
  val customerCount: Int
)

@Composable
fun RoomBackupModal(
  viewModel: InvoiceViewModel,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()

  var isSavingBackup by remember { mutableStateOf(false) }
  var savedSuccessInfo by remember { mutableStateOf<BackupSavedSuccessInfo?>(null) }
  var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

  // Custom SAF Save Document Launcher (if user wants to pick an alternative folder)
  val customSaveFileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json")
  ) { uri ->
    uri?.let {
      viewModel.saveBackupToUri(context, it)
    }
  }

  // File Picker Launcher for Restoring Backup from phone storage
  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    uri?.let {
      pendingRestoreUri = it
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Card(
        modifier = Modifier
          .fillMaxWidth(0.95f)
          .padding(vertical = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
        ) {
          // Modal Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(46.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF16A34A).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Storage,
                  contentDescription = null,
                  tint = Color(0xFF16A34A),
                  modifier = Modifier.size(24.dp)
                )
              }
              Column {
                Text(
                  text = "النسخ الاحتياطي واستعادة البيانات",
                  fontSize = 17.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF0F172A)
                )
                Text(
                  text = "حفظ وتصدير واستعادة البيانات في وحدة التخزين الداخلية للهاتف",
                  fontSize = 11.5.sp,
                  color = Color(0xFF64748B)
                )
              }
            }
            IconButton(onClick = onDismiss) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
            }
          }

          HorizontalDivider(
            modifier = Modifier.padding(vertical = 14.dp),
            color = Color(0xFFE2E8F0)
          )

          // 1. الخيار الأول: النسخة الاحتياطية التلقائية
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (uiState.isAutoDailyBackupEnabled) Color(0xFFF0FDF4) else Color.White
            ),
            border = BorderStroke(
              1.5.dp,
              if (uiState.isAutoDailyBackupEnabled) Color(0xFF86EFAC) else Color(0xFFE2E8F0)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (uiState.isAutoDailyBackupEnabled) Color(0xFF16A34A) else Color(0xFF94A3B8),
                    modifier = Modifier.size(36.dp)
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text("🔄", fontSize = 16.sp)
                    }
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Text(
                        text = "النسخة الاحتياطية التلقائية",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (uiState.isAutoDailyBackupEnabled) Color(0xFF14532D) else Color(0xFF1E293B)
                      )
                      Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (uiState.isAutoDailyBackupEnabled) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                      ) {
                        Text(
                          text = if (uiState.isAutoDailyBackupEnabled) "مُفعلة" else "مُعطلة",
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Bold,
                          color = if (uiState.isAutoDailyBackupEnabled) Color(0xFF15803D) else Color(0xFF64748B),
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                      }
                    }
                  }
                }

                Switch(
                  checked = uiState.isAutoDailyBackupEnabled,
                  onCheckedChange = { viewModel.setAutoDailyBackupEnabled(it) },
                  colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF16A34A),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFCBD5E1)
                  )
                )
              }

              Text(
                text = "عند تفعيلها، يتم عمل نسخة احتياطية وتصديرها تلقائياً إلى وحدة التخزين الداخلية للهاتف عند الدخول للتطبيق لأول مرة (مرة واحدة في اليوم).",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = if (uiState.isAutoDailyBackupEnabled) Color(0xFF166534) else Color(0xFF64748B)
              )

              if (uiState.isAutoDailyBackupEnabled) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = Color(0xFFDCFCE7).copy(alpha = 0.6f),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Text(
                      text = if (uiState.lastAutoBackupDate.isNotEmpty())
                        "📅 تاريخ آخر نسخة تلقائية: ${uiState.lastAutoBackupDate}"
                      else
                        "📅 بانتظار إتمام أول تصدير تلقائي لليوم",
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF15803D)
                    )
                    Text(
                      text = "📁 مجلد التصدير بالهاتف: وحدة التخزين الداخلية / Download / Mamlaka_Backups",
                      fontSize = 10.5.sp,
                      color = Color(0xFF166534)
                    )
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // 2. الزر الثاني: حفظ النسخة الاحتياطية (تصدير فوري لوحدة التخزين الداخلية)
          BigBackupActionButton(
            title = "حفظ النسخة الاحتياطية",
            subtitle = "تصدير وحفظ فوري للنسخة في وحدة التخزين الداخلية للهاتف",
            icon = Icons.Default.Save,
            containerColor = Color(0xFF15803D),
            isLoading = isSavingBackup,
            onClick = {
              if (!isSavingBackup) {
                isSavingBackup = true
                viewModel.saveBackupToInternalStorage(context) { success, fileName, displayPath, invCount, custCount ->
                  isSavingBackup = false
                  if (success) {
                    savedSuccessInfo = BackupSavedSuccessInfo(
                      fileName = fileName,
                      displayPath = displayPath,
                      invoiceCount = invCount,
                      customerCount = custCount
                    )
                  }
                }
              }
            }
          )

          Spacer(modifier = Modifier.height(14.dp))

          // 3. الخيار الثالث: استعادة النسخة الاحتياطية من الهاتف
          BigBackupActionButton(
            title = "استعادة النسخة الاحتياطية",
            subtitle = "اختيار ملف النسخة الاحتياطية من ذاكرة الهاتف واستعادة البيانات",
            icon = Icons.Default.Restore,
            containerColor = Color(0xFF1D4ED8),
            isLoading = false,
            onClick = {
              filePickerLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            }
          )
        }
      }
    }
  }

  // ==================== نافذة تأكيد نجاح حفظ النسخة الاحتياطية ====================
  savedSuccessInfo?.let { info ->
    AlertDialog(
      onDismissRequest = { savedSuccessInfo = null },
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text("💾", fontSize = 18.sp)
          Text(
            text = "تم حفظ النسخة الاحتياطية بنجاح",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF15803D)
          )
        }
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "تم تصدير وحفظ النسخة الاحتياطية في وحدة التخزين الداخلية للهاتف بنجاح:",
            fontSize = 12.5.sp,
            color = Color(0xFF334155)
          )

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF1F5F9),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = "📁 المسار: ${info.displayPath}",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
              )
              Text(
                text = "📄 الملف: ${info.fileName}",
                fontSize = 11.sp,
                color = Color(0xFF475569)
              )
              Text(
                text = "📊 البيانات: ${info.invoiceCount} فاتورة | ${info.customerCount} حساب عميل",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF15803D)
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { savedSuccessInfo = null },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
        ) {
          Text("تم وموافق", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = {
            savedSuccessInfo = null
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            customSaveFileLauncher.launch("Mamlaka_Backup_$timeStamp.json")
          }
        ) {
          Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("حفظ بمجلد مخصص آخر", fontSize = 11.5.sp)
        }
      }
    )
  }

  // ==================== نافذة تأكيد استعادة النسخة الاحتياطية ====================
  pendingRestoreUri?.let { uri ->
    AlertDialog(
      onDismissRequest = { pendingRestoreUri = null },
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text("⚠️", fontSize = 18.sp)
          Text(
            text = "تأكيد استعادة النسخة الاحتياطية",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFDC2626)
          )
        }
      },
      text = {
        Text(
          text = "هل أنت متأكد من استعادة هذه النسخة الاحتياطية من ذاكرة الهاتف؟\n\nسيتم تحديث واستبدال بيانات الفواتير، الحسابات والإعدادات الحالية في التطبيق بمحتوى الملف المختار.",
          fontSize = 13.sp,
          lineHeight = 19.sp,
          color = Color(0xFF334155)
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.importBackupFromUri(context, uri)
            pendingRestoreUri = null
            onDismiss()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) {
          Text("نعم، استعادة الآن", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { pendingRestoreUri = null }) {
          Text("إلغاء")
        }
      }
    )
  }
}

@Composable
private fun BigBackupActionButton(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  containerColor: Color,
  isLoading: Boolean,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.97f else 1.0f,
    animationSpec = spring(stiffness = Spring.StiffnessHigh)
  )

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .height(64.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(RoundedCornerShape(14.dp))
      .clickable(
        interactionSource = interactionSource,
        indication = ripple(color = Color.White.copy(alpha = 0.3f)),
        onClick = onClick
      ),
    shape = RoundedCornerShape(14.dp),
    color = containerColor,
    shadowElevation = if (isPressed) 1.dp else 4.dp
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.22f),
        modifier = Modifier.size(42.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = Color.White,
              strokeWidth = 2.dp
            )
          } else {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(22.dp)
            )
          }
        }
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = title,
          fontSize = 14.5.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
        Text(
          text = subtitle,
          fontSize = 10.5.sp,
          color = Color.White.copy(alpha = 0.9f)
        )
      }
    }
  }
}
