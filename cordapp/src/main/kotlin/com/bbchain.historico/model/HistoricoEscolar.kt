package com.bbchain.historico.model

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

@CordaSerializable
data class HistoricoEscolar(
       val idAluno: Int,
       val nomeCurso: String,
       val dataInicio: Instant,
       val nota: Int,
       val cargaHoraria: Int,
       val faculdade: Party
)
