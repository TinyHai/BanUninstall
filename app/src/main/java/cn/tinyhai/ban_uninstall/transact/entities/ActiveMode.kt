package cn.tinyhai.ban_uninstall.transact.entities

enum class ActiveMode(val description: String) {
    Disabled("Disabled"),
    Xposed("Xposed"),
    Root("Root(LSPatch)")
}