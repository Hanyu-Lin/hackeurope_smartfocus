package locked.`in`.domain.model

data class FilterRule(
    val id: String,
    val focusModeId: String,
    val type: RuleType,
    val value: String,
    val effect: RuleEffect,
    val action: RuleAction,
    val sortOrder: Int = 0
)
