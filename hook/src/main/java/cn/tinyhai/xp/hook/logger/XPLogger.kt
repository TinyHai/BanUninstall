package cn.tinyhai.xp.hook.logger

interface XPLogger {
    fun info(s: String)
    fun debug(s: String)
    fun verbose(s: String)
    fun error(s: String)
    fun error(th: Throwable)
    fun error(s: String, th: Throwable)

    companion object : XPLogger {
        override fun info(s: String) {}

        override fun debug(s: String) {}

        override fun verbose(s: String) {}

        override fun error(s: String) {}

        override fun error(th: Throwable) {}

        override fun error(s: String, th: Throwable) {}
    }
}

class XPLoggerWrapper(var realLogger: XPLogger = XPLogger) : XPLogger {
    override fun info(s: String) {
        realLogger.info(s)
    }

    override fun debug(s: String) {
        realLogger.debug(s)
    }

    override fun verbose(s: String) {
        realLogger.verbose(s)
    }

    override fun error(s: String) {
        realLogger.error(s)
    }

    override fun error(th: Throwable) {
        realLogger.error(th)
    }

    override fun error(s: String, th: Throwable) {
        realLogger.error(s, th)
    }
}