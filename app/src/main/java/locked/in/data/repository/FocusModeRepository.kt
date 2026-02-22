package locked.`in`.data.repository

import kotlinx.coroutines.flow.Flow
import locked.`in`.domain.model.FilterRule
import locked.`in`.domain.model.FocusMode
import java.time.DayOfWeek

interface FocusModeRepository {
    fun observeAll(): Flow<List<FocusMode>>
    fun observeActive(): Flow<List<FocusMode>>
    fun observeById(id: String): Flow<FocusMode?>
    suspend fun getById(id: String): FocusMode?
    suspend fun getActive(): List<FocusMode>
    suspend fun insert(mode: FocusMode)
    suspend fun update(mode: FocusMode)
    suspend fun updateName(id: String, name: String)
    suspend fun updatePriorityThreshold(id: String, threshold: Float)
    suspend fun updateScheduleEnabled(id: String, enabled: Boolean)
    suspend fun updateScheduleDays(id: String, days: Set<DayOfWeek>)
    suspend fun updateScheduleStartMinute(id: String, minute: Int)
    suspend fun updateScheduleEndMinute(id: String, minute: Int)
    suspend fun updateTimerEnabled(id: String, enabled: Boolean)
    suspend fun updateTimerDurationMinutes(id: String, minutes: Int)
    suspend fun deleteById(id: String)
    suspend fun activate(id: String)
    suspend fun deactivate()
    suspend fun deactivateMode(id: String)
    suspend fun getRulesForMode(focusModeId: String): List<FilterRule>
    fun observeRulesForMode(focusModeId: String): Flow<List<FilterRule>>
    suspend fun insertRule(rule: FilterRule)
    suspend fun updateRule(rule: FilterRule)
    suspend fun deleteRule(ruleId: String)
    suspend fun getRuleById(ruleId: String): FilterRule?
    suspend fun getScheduledModes(): List<FocusMode>
}
