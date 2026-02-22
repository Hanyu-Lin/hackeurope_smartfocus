package locked.`in`.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import locked.`in`.data.local.dao.FilterRuleDao
import locked.`in`.data.local.dao.FocusModeDao
import locked.`in`.data.local.entity.FilterRuleEntity
import locked.`in`.data.local.entity.FocusModeEntity
import locked.`in`.domain.model.FilterRule
import locked.`in`.domain.model.FocusMode
import locked.`in`.domain.model.RuleAction
import locked.`in`.domain.model.RuleEffect
import locked.`in`.domain.model.RuleType
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusModeRepositoryImpl @Inject constructor(
    private val focusModeDao: FocusModeDao,
    private val filterRuleDao: FilterRuleDao
) : FocusModeRepository {

    override fun observeAll(): Flow<List<FocusMode>> =
        combine(
            focusModeDao.observeAll(),
            filterRuleDao.observeAll()
        ) { entities, allRules ->
            val rulesByMode = allRules.groupBy { it.focusModeId }
            entities.map { entity ->
                val rules = (rulesByMode[entity.id] ?: emptyList()).map { it.toDomain() }
                entity.toDomain(rules)
            }
        }

    override fun observeActive(): Flow<List<FocusMode>> =
        combine(
            focusModeDao.observeActiveModes(),
            filterRuleDao.observeAll()
        ) { entities, allRules ->
            val rulesByMode = allRules.groupBy { it.focusModeId }
            entities.map { entity ->
                val rules = (rulesByMode[entity.id] ?: emptyList()).map { it.toDomain() }
                entity.toDomain(rules)
            }
        }

    override fun observeById(id: String): Flow<FocusMode?> =
        focusModeDao.observeById(id).map { entity ->
            entity?.let {
                val rules = filterRuleDao.getByFocusModeId(it.id).map { r -> r.toDomain() }
                it.toDomain(rules)
            }
        }

    override suspend fun getById(id: String): FocusMode? {
        val entity = focusModeDao.getById(id) ?: return null
        val rules = filterRuleDao.getByFocusModeId(id).map { it.toDomain() }
        return entity.toDomain(rules)
    }

    override suspend fun getActive(): List<FocusMode> {
        return focusModeDao.getActiveModes().map { entity ->
            val rules = filterRuleDao.getByFocusModeId(entity.id).map { it.toDomain() }
            entity.toDomain(rules)
        }
    }

    override suspend fun insert(mode: FocusMode) {
        focusModeDao.insert(mode.toEntity())
    }

    override suspend fun update(mode: FocusMode) {
        focusModeDao.update(mode.toEntity())
    }

    override suspend fun deleteById(id: String) {
        focusModeDao.deleteById(id)
    }

    override suspend fun activate(id: String) {
        focusModeDao.activate(id)
    }

    override suspend fun deactivate() {
        focusModeDao.deactivateAll()
    }

    override suspend fun deactivateMode(id: String) {
        focusModeDao.deactivateMode(id)
    }

    override suspend fun getRulesForMode(focusModeId: String): List<FilterRule> =
        filterRuleDao.getByFocusModeId(focusModeId).map { it.toDomain() }

    override fun observeRulesForMode(focusModeId: String): Flow<List<FilterRule>> =
        filterRuleDao.observeByFocusModeId(focusModeId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun insertRule(rule: FilterRule) {
        filterRuleDao.insert(rule.toEntity())
    }

    override suspend fun updateRule(rule: FilterRule) {
        filterRuleDao.update(rule.toEntity())
    }

    override suspend fun deleteRule(ruleId: String) {
        filterRuleDao.deleteById(ruleId)
    }

    override suspend fun getRuleById(ruleId: String): FilterRule? =
        filterRuleDao.getById(ruleId)?.toDomain()

    override suspend fun getScheduledModes(): List<FocusMode> =
        focusModeDao.getScheduledModes().map { entity ->
            val rules = filterRuleDao.getByFocusModeId(entity.id).map { it.toDomain() }
            entity.toDomain(rules)
        }

    private fun FocusModeEntity.toDomain(rules: List<FilterRule>) = FocusMode(
        id = id, name = name, isActive = isActive, rules = rules, priorityThreshold = priorityThreshold,
        scheduleEnabled = scheduleEnabled,
        scheduleDays = scheduleDays.toDayOfWeekSet(),
        scheduleStartMinute = scheduleStartMinute,
        scheduleEndMinute = scheduleEndMinute,
        timerEnabled = timerEnabled,
        timerDurationMinutes = timerDurationMinutes
    )

    private fun FocusMode.toEntity() = FocusModeEntity(
        id = id, name = name, isActive = isActive, priorityThreshold = priorityThreshold,
        scheduleEnabled = scheduleEnabled,
        scheduleDays = scheduleDays.toDaysString(),
        scheduleStartMinute = scheduleStartMinute,
        scheduleEndMinute = scheduleEndMinute,
        timerEnabled = timerEnabled,
        timerDurationMinutes = timerDurationMinutes
    )

    private fun String.toDayOfWeekSet(): Set<DayOfWeek> {
        if (isBlank()) return emptySet()
        return split(",").mapNotNull { it.trim().toIntOrNull()?.let { v -> DayOfWeek.of(v) } }.toSet()
    }

    private fun Set<DayOfWeek>.toDaysString(): String =
        sorted().joinToString(",") { it.value.toString() }

    private fun FilterRuleEntity.toDomain() = FilterRule(
        id = id, focusModeId = focusModeId,
        type = RuleType.valueOf(type), value = value,
        effect = RuleEffect.valueOf(effect), action = RuleAction.valueOf(action),
        sortOrder = sortOrder
    )

    private fun FilterRule.toEntity() = FilterRuleEntity(
        id = id, focusModeId = focusModeId,
        type = type.name, value = value,
        effect = effect.name, action = action.name,
        sortOrder = sortOrder
    )
}
