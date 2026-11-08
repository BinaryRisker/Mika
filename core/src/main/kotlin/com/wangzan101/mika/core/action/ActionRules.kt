package com.wangzan101.mika.core.action

import com.wangzan101.mika.core.gate.ConfirmationGate
import com.wangzan101.mika.core.net.UrlHost
import com.wangzan101.mika.core.run.Attendance
import com.wangzan101.mika.core.run.RunOrigin
import com.wangzan101.mika.core.run.RunProvenance
import com.wangzan101.mika.core.uistate.ScreenSnapshot

/**
 * 一次运行开工时读入的发送和删除规矩。空的允许名单表示发送都要问。
 * 禁止按包名单独关送达、删除、付款，并且优先于允许名单和自主模式。
 */
data class ActionRules(
    val allowSend: List<SendAllow> = emptyList(),
    val deny: List<PackageDeny> = emptyList(),
) {
    /** 包名，或网页的可注册域名，加上标题栏里的对象名。 */
    data class SendAllow(val scope: String, val objectName: String)

    /** 这个包不许做的那几项。没命中的项不关。 */
    data class PackageDeny(
        val packageName: String,
        val send: Boolean = false,
        val delete: Boolean = false,
        val payment: Boolean = false,
    )

    enum class Kind { Send, Delete, Payment }

    fun denies(packageName: String?, kind: Kind): Boolean {
        if (packageName.isNullOrBlank()) return false
        return deny.any { it.packageName == packageName && it.blocks(kind) }
    }

    /**
     * 对象必须出现在标题栏里，核对方式和 [ConfirmationGate] 认发送对象的方式相同。
     * 对不上就当成不在名单里。
     */
    fun allowsSend(screen: ScreenSnapshot, titles: ConfirmationGate): Boolean =
        allowSend.any { entry ->
            entry.objectName.isNotBlank() && scopeMatches(entry.scope, screen) && titles.showsSendTarget(entry.objectName, screen)
        }

    private fun PackageDeny.blocks(kind: Kind) = when (kind) {
        Kind.Send -> send
        Kind.Delete -> delete
        Kind.Payment -> payment
    }

    companion object {
        val NONE = ActionRules()

        /** 手机上发起、并且不是无人值守，才问得了人。定时、渠道、电脑和子任务都不算。 */
        fun personPresent(run: RunProvenance) =
            run.origin == RunOrigin.Ui && run.attendance != Attendance.Unattended

        /** 允许项是包名，或网页的可注册域名。网页不拿浏览器包名去对。 */
        fun scopeMatches(scope: String, screen: ScreenSnapshot): Boolean {
            val want = scope.trim()
            if (want.isEmpty()) return false
            val web = screen.web
            if (web != null) {
                val host = UrlHost.of(web.url) ?: return false
                val page = UrlHost.registrable(host) ?: host
                val given = if ("://" in want) UrlHost.of(want) ?: return false else want.removePrefix("www.").lowercase()
                val domain = UrlHost.registrable(given) ?: given
                return page.equals(domain, ignoreCase = true)
            }
            return screen.packageName == want
        }

        /** 要发出去的正文：这一步正在输入的字，否则是输入框里的字。 */
        fun messageBody(screen: ScreenSnapshot?, typing: String?): String {
            if (!typing.isNullOrEmpty()) return typing
            val fields = screen?.elements.orEmpty().filter { it.editable && !it.hint && !it.secret && it.name.isNotBlank() }
            return (fields.firstOrNull { it.focused } ?: fields.firstOrNull())?.name?.trim().orEmpty()
        }
    }
}
