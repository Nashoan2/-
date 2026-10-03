package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerReminder
import com.example.util.ArabicNumberHelper

@Composable
fun DueRemindersDialog(
  dueReminders: List<CustomerReminder>,
  onOpenReminders: () -> Unit,
  onDismiss: () -> Unit
) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    AlertDialog(
      onDismissRequest = onDismiss,
      shape = RoundedCornerShape(16.dp),
      containerColor = Color.White,
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(Color(0xFFFEF3C7)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsActive,
              contentDescription = "تنبيه",
              tint = Color(0xFFD97706),
              modifier = Modifier.size(24.dp)
            )
          }
          Column {
            Text(
              text = "شعار تنبيه: مواعيد مستحقة!",
              fontWeight = FontWeight.Black,
              fontSize = 17.sp,
              color = Color(0xFFB45309)
            )
            Text(
              text = "لديك ${dueReminders.size} تنبيهات ومواعيد مستحقة المتابعة",
              fontSize = 12.sp,
              color = Color.Gray
            )
          }
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          dueReminders.take(6).forEach { rem ->
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
              border = BorderStroke(1.dp, Color(0xFFFDE68A)),
              shape = RoundedCornerShape(10.dp)
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = rem.customerName,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp,
                    color = Color(0xFF1E293B)
                  )
                  if (rem.amountDue > 0) {
                    Text(
                      text = "${ArabicNumberHelper.formatAmount(rem.amountDue)} $",
                      fontWeight = FontWeight.Black,
                      fontSize = 13.sp,
                      color = Color(0xFF7E22CE)
                    )
                  }
                }

                Text(
                  text = "البيان: ${rem.title}",
                  fontSize = 12.sp,
                  color = Color(0xFF475569)
                )

                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                  ) {
                    Icon(
                      Icons.Default.CalendarMonth,
                      contentDescription = null,
                      tint = Color(0xFFD97706),
                      modifier = Modifier.size(13.dp)
                    )
                    Text(
                      text = rem.dueDate,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF92400E)
                    )
                  }

                  if (rem.dueTime.isNotBlank()) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                      Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(13.dp)
                      )
                      Text(
                        text = rem.dueTime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8)
                      )
                    }
                  }
                }
              }
            }
          }

          if (dueReminders.size > 6) {
            Text(
              text = "+ ويوجد ${dueReminders.size - 6} مواعيد أخرى في قائمة التنبيهات",
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFD97706)
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = onOpenReminders,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
          Text("📋 فتح ومتابعة التنبيهات", fontWeight = FontWeight.Bold, color = Color.White)
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = onDismiss,
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
          Text("إغلاق", fontWeight = FontWeight.Bold, color = Color(0xFF555555))
        }
      }
    )
  }
}
