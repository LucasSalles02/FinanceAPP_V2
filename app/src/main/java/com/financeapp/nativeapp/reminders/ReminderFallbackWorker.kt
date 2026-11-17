package com.financeapp.nativeapp.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Reserva de entrega se o alarme nativo foi adiado ou removido pelo Android.
 * Não garante disparo exato; segue as restrições de bateria e de Forçar parada.
 */
class ReminderFallbackWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val reminderId = inputData.getInt(ReminderScheduler.EXTRA_REMINDER_ID, -1)
        if (reminderId < 0) return Result.success()
        val reminder = ReminderStore.find(applicationContext, reminderId) ?: return Result.success()
        if (reminder.status != ReminderStatus.SCHEDULED) return Result.success()
        // O agendamento pode ter sido editado desde a criação deste trabalho.
        if (reminder.scheduledAt > System.currentTimeMillis()) return Result.success()
        ReminderReceiver.deliver(applicationContext, reminderId, "reserva_workmanager")
        return Result.success()
    }
}
