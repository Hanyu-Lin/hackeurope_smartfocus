package locked.`in`.data.repository

import kotlinx.coroutines.flow.Flow
import locked.`in`.domain.model.FilterRule
import locked.`in`.domain.model.FocusMode

interface FocusModeRepository {
    fun observeAll(): Flow<List<FocusMode>>
    fun observeActive(): Flow<FocusMode?>
    suspend fun getById(id: String): FocusMode?
    suspend fun getActive(): FocusMode?
    suspend fun insert(mode: FocusMode)
    suspend fun update(mode: FocusMode)
    suspend fun deleteById(id: String)
    suspend fun activate(id: String)
    suspend fun deactivate()
    suspend fun getRulesForMode(focusModeId: String): List<FilterRule>
    fun observeRulesForMode(focusModeId: String): Flow<List<FilterRule>>
    suspend fun insertRule(rule: FilterRule)
    suspend fun updateRule(rule: FilterRule)
    suspend fun deleteRule(ruleId: String)
    suspend fun getRuleById(ruleId: String): FilterRule?
    suspend fun getScheduledModes(): List<FocusMode>
}
