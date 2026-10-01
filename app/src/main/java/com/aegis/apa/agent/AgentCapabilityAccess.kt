package com.aegis.apa.agent

import com.aegis.apa.model.ShizukuAccessState

data class AgentCapabilityAccess(
    val shizukuAuthorized: Boolean,
    val rootAuthorized: Boolean
) {
    companion object {
        fun from(
            shizukuAccessState: ShizukuAccessState,
            rootAuthorized: Boolean
        ): AgentCapabilityAccess = AgentCapabilityAccess(
            shizukuAuthorized = shizukuAccessState == ShizukuAccessState.AUTHORIZED,
            rootAuthorized = rootAuthorized
        )
    }
}
