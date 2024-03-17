package me.yuanis.copilot.gpt.service.server

/**
 * Server status
 */
sealed interface ServerStatus {

    fun isRunning(): Boolean = this is Running

    /**
     * Server is stopped
     */
    data object Stopped : ServerStatus

    /**
     * Server is running
     *
     * @param port Server port
     */
    class Running(val port: Int) : ServerStatus
}
