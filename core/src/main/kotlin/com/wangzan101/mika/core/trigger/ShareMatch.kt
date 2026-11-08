package com.wangzan101.mika.core.trigger

/**
 * 分享进来时按启用中的分享常规和接受类型匹配；常用说法是启用中的手动常规。
 * 选定之后才把分享正文接到常规目标后面开跑。
 */
object ShareMatch {
    fun matching(routines: List<TriggerDefinition>, type: ShareType): List<TriggerDefinition> =
        routines.filter { it.enabled && it.kind == RoutineKind.Share && type in it.shareTypes }

    fun phrases(routines: List<TriggerDefinition>): List<TriggerDefinition> =
        routines.filter { it.enabled && it.kind == RoutineKind.Manual }

    fun shareType(hasImage: Boolean): ShareType = if (hasImage) ShareType.Image else ShareType.Text

    /** 选定常规之后交给这一次运行的目标：常规原话在前，分享正文在后。 */
    fun composeGoal(routineGoal: String, sharedText: String): String {
        val goal = routineGoal.trim()
        val text = sharedText.trim()
        return when {
            text.isEmpty() -> goal
            goal.isEmpty() -> text
            else -> "$goal\n\n$text"
        }
    }
}
