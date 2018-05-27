package com.bbchain.historico.flow

import com.bbchain.historico.model.HistoricoEscolar
import com.bbchain.historico.state.HistoricoEscolarState
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnviarHistoricoEscolarFlowTest {
   lateinit var network: MockNetwork
   lateinit var a: StartedMockNode
   lateinit var b: StartedMockNode
   lateinit var disciplinaState: HistoricoEscolarState

   @Before
   fun setup() {
       network = MockNetwork(listOf("com.br.bbchain.certificates.contract"))
       a = network.createPartyNode()
       b = network.createPartyNode()
       // For real nodes this happens automatically, but we have to manually register the flow for tests.
       listOf(a, b).forEach { it.registerInitiatedFlow(EnviarHistoricoFlow.RespFlow::class.java) }

       network.runNetwork()
       disciplinaState = criarDisciplina()
   }

   fun criarDisciplina() : HistoricoEscolarState {
       val disciplina = HistoricoEscolar(idAluno = 1,
               cargaHoraria = 0,
               dataInicio = Instant.now(),
               faculdade = a.info.legalIdentities.first(),
               nomeCurso = "Corda",
               nota = 10)

       val flow = ArmazenarHistoricoFlow.ReqFlow(disciplina)
       val future = a.startFlow(flow)
       network.runNetwork()

       val signedTransaction = future.get()
       val output = signedTransaction.coreTransaction.outputsOfType<HistoricoEscolarState>().single()

       return output
   }

   @After
   fun tearDown() {
       network.stopNodes()
   }

   @Test
   fun `deve enviar historico escolar`() {

       val flow = EnviarHistoricoFlow.ReqFlow(disciplinaState.linearId.id, b.info.legalIdentities.first())
       val future = a.startFlow(flow)
       network.runNetwork()

       val signedTransaction = future.get()
       val outputState = signedTransaction.coreTransaction.outputsOfType<HistoricoEscolarState>().single()
       assertEquals(outputState.historicoEscolar, disciplinaState.historicoEscolar)
       assertTrue(outputState.faculdadesReceptoras.containsAll(disciplinaState.faculdadesReceptoras))
       assertTrue(disciplinaState.faculdadesReceptoras.size + 1 == outputState.faculdadesReceptoras.size)

       listOf(a, b).forEach {
           val vaultState = it.services.vaultService.queryBy<HistoricoEscolarState>(
                   QueryCriteria.LinearStateQueryCriteria(
                           linearId = listOf(disciplinaState.linearId))).states.single().state.data
       assertEquals(vaultState, outputState)
       }
   }

}
