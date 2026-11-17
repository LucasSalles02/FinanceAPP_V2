package com.financeapp.nativeapp.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.financeapp.nativeapp.MainActivity
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Notificações 100% locais. AlarmManager + PendingIntent explícito permanecem válidos
 * mesmo quando a Activity não está aberta (exceto após Forçar parada do aplicativo).
 * Sem a autorização de alarmes precisos, o disparo continua aproximado e a UI avisa.
 * Alarmes não sobrevivem ao estado "Forçar parada" imposto pelo Android.
 */
object ReminderScheduler {
    // Um novo ID permite que instalações anteriores recebam um canal de alerta visível;
    // a importância de um canal Android já criado não pode ser elevada por código.
    const val CHANNEL_ID = "financeapp_financial_alerts_v2"
    const val EXTRA_REMINDER_ID = "financeapp_reminder_id"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Alertas financeiros",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos locais sobre pagamentos e compromissos agendados"
                    enableVibration(true)
                }
            )
        }
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    /** Android 12+: só é possível agendar com exatidão quando o usuário permite.
     * Em versões anteriores o sistema permite o agendamento exato diretamente.
     */
    fun canUseExactAlarms(context: Context): Boolean {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
    }

    fun schedule(context: Context, reminder: FinanceReminder): Boolean {
        if (reminder.scheduledAt <= System.currentTimeMillis()) return false
        createChannel(context)
        if (!canNotify(context)) return false
        return try {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = pendingIntent(context, reminder.id, PendingIntent.FLAG_UPDATE_CURRENT)
            // FinanceAPP é um app de lembretes escolhidos pelo próprio usuário.
            // Quando autorizado, usamos AlarmClockInfo: o Android trata esse alerta
            // como prioritário e o entrega mesmo em Doze. O sistema também pode
            // exibir o ícone de próximo alarme na barra de status.
            // Sem autorização explícita, só podemos oferecer horário aproximado.
            val preciseScheduled = if (canUseExactAlarms(context)) {
                try {
                    alarm.setAlarmClock(
                        AlarmManager.AlarmClockInfo(reminder.scheduledAt, alarmDisplayIntent(context, reminder.id)),
                        intent
                    )
                    true
                } catch (e: SecurityException) {
                    Log.w("FinanceReminder", "Permissão exata revogada durante agendamento id=${reminder.id}", e)
                    false
                }
            } else false
            if (!preciseScheduled) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.scheduledAt, intent)
                Log.w("FinanceReminder", "Agendamento aproximado id=${reminder.id}: alarmes precisos não autorizados")
            } else {
                Log.i("FinanceReminder", "Agendamento prioritário id=${reminder.id} em ${reminder.scheduledAt}")
            }
            // Reserva um disparo alternativo com WorkManager. O alarme permanece
            // o mecanismo principal; a tarefa adicional atua apenas se ele falhar.
            scheduleBackup(context, reminder)
            true
        } catch (e: Exception) {
            Log.e("FinanceReminder", "Falha ao agendar alarme ${reminder.id}", e)
            false
        }
    }

    fun cancel(context: Context, id: Int): Boolean {
        return try {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = pendingIntent(context, id, PendingIntent.FLAG_NO_CREATE)
            if (intent != null) {
                alarm.cancel(intent)
                intent.cancel()
            }
            // O intent de abertura é apenas um atalho do alarme de alta prioridade.
            alarmDisplayIntentIfExists(context, id)?.cancel()
            // A tarefa reserva também deve ser cancelada ao editar ou excluir.
            WorkManager.getInstance(context).cancelUniqueWork(backupName(id))
            true
        } catch (e: Exception) {
            Log.e("FinanceReminder", "Falha ao cancelar alarme $id", e)
            false
        }
    }

    /** Reagendar somente os itens futuros: atualização do app ou reinício do Android. */
    fun restorePending(context: Context, deviceRebooted: Boolean = false) {
        createChannel(context)
        val now = System.currentTimeMillis()
        ReminderStore.list(context)
            .filter { it.status == ReminderStatus.SCHEDULED }
            .forEach { reminder ->
                if (reminder.scheduledAt > now) {
                    // Mesmo ID substitui o PendingIntent: sem agendamentos duplicados.
                    schedule(context, reminder)
                } else if (reminder.repeatMonthly && now - reminder.scheduledAt > TimeUnit.HOURS.toMillis(24)) {
                    // A V6 descartava o aviso mensal assim que o relógio passava
                    // do horário agendado, ainda que o receiver estivesse por disparar.
                    // Não invalidamos alarmes atrasados pelo Android no próprio dia.
                    if (ReminderStore.setStatus(context, reminder.id, ReminderStatus.MISSED)) {
                        ReminderRecurrence.createNext(context, reminder, now)
                    }
                } else if (deviceRebooted && now - reminder.scheduledAt > TimeUnit.HOURS.toMillis(24)) {
                    // Um alarme vencido durante o desligamento NÃO foi entregue.
                    ReminderStore.setStatus(context, reminder.id, ReminderStatus.MISSED)
                }
            }
    }

    private fun backupName(id: Int) = "finance_reminder_backup_$id"

    private fun scheduleBackup(context: Context, reminder: FinanceReminder) {
        try {
            // A entrega alternativa é aproximada, deliberadamente posterior ao alarme nativo.
            // WorkManager retém a tarefa se o processo do aplicativo for encerrado.
            val delayMs = (reminder.scheduledAt + TimeUnit.MINUTES.toMillis(2)
                - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<ReminderFallbackWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(EXTRA_REMINDER_ID to reminder.id))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                backupName(reminder.id), ExistingWorkPolicy.REPLACE, request
            )
        } catch (e: Exception) {
            // Falhar a reserva NÃO desfaz o agendamento principal já realizado.
            Log.w("FinanceReminder", "Reserva WorkManager indisponível: ${reminder.id}", e)
        }
    }


    private fun alarmDisplayIntent(context: Context, id: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun alarmDisplayIntentIfExists(context: Context, id: Int): PendingIntent? =
        PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    private fun pendingIntent(context: Context, id: Int, flags: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, id)
        }
        return PendingIntent.getBroadcast(
            context, id, intent, flags or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
