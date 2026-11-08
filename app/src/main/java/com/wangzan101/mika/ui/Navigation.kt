package com.wangzan101.mika.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** App 里的页面。今天是根页面,对话从它进去,返回时回到今天;从通知点进来则是那条对话。 */
enum class Route { Today, Chat, Settings, Memory, Plans, Browser, Run, Source, PhoneSetup, Models, Usage, SpeechModel, Welcome, ModelSetup, About, License, Screens }

/**
 * 页面栈:最底下永远是今天。[push] 进一页,[back] 回上一页(已在今天时返回 false,交给系统退出)。
 * 返回键和页面上的返回按钮走同一个 [back],所以两者总是一致。
 */
@Stable
class NavStack(initial: List<Route> = listOf(Route.Today), fromDrawer: Boolean = false) {
    var stack: List<Route> by mutableStateOf(normalize(initial))
        private set

    /** 这一串页面是从侧栏进去的:退回今天时要回到打开的侧栏。 */
    private var fromDrawer = fromDrawer && stack.size > 1

    /** 刚从侧栏进去的页面退回到了今天,侧栏该重新打开;打开后调 [drawerReopened]。 */
    var reopenDrawer: Boolean by mutableStateOf(false)
        private set

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** 进一页;已经在这一页时不重复压栈。今天和对话各有专用入口。 */
    fun push(route: Route) {
        if (route == Route.Today) return goHome()
        if (route == Route.Chat) return openChat()
        if (current != route) stack = stack + route
    }

    /** 从侧栏进一页:之后一路退回今天时,侧栏是打开的。 */
    fun pushFromDrawer(route: Route) {
        fromDrawer = true
        push(route)
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        stack = stack.dropLast(1)
        if (!canGoBack && fromDrawer) {
            fromDrawer = false
            reopenDrawer = true
        }
        return true
    }

    fun drawerReopened() {
        reopenDrawer = false
    }

    /** 只留今天和 [route] 两层(首次上手的每一步用)。 */
    fun show(route: Route) {
        fromDrawer = false
        stack = normalize(listOf(route))
    }

    /** 回到今天这一页。 */
    fun goHome() {
        fromDrawer = false
        reopenDrawer = false
        stack = listOf(Route.Today)
    }

    /** 打开对话(通知、计划、今天这一页点进去):今天在底下,返回回到今天。 */
    fun openChat() {
        fromDrawer = false
        reopenDrawer = false
        stack = listOf(Route.Today, Route.Chat)
    }

    companion object {
        private const val DRAWER_MARK = "fromDrawer"

        private fun normalize(list: List<Route>): List<Route> =
            listOf(Route.Today) + list.filter { it != Route.Today }

        val Saver: Saver<NavStack, ArrayList<String>> = Saver(
            save = { ArrayList(it.stack.map(Route::name) + listOfNotNull(DRAWER_MARK.takeIf { _ -> it.fromDrawer })) },
            restore = { names ->
                NavStack(names.mapNotNull { n -> Route.entries.firstOrNull { it.name == n } }, fromDrawer = DRAWER_MARK in names)
            },
        )
    }
}

@Composable
fun rememberNavStack(): NavStack = rememberSaveable(saver = NavStack.Saver) { NavStack() }
