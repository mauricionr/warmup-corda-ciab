package com.bbchain.historico.flow

import co.paralleluniverse.fibers.Suspendable
import com.bbchain.historico.contract.HistoricoEscolarContract
import com.bbchain.historico.state.HistoricoEscolarState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*

object EnviarHistoricoFlow {

   @InitiatingFlow
   class ReqFlow(val historicoId: UUID, val para: Party): FlowLogic<SignedTransaction>() {

       @Suspendable
       override fun call(): SignedTransaction {
           // Encontramos a transação com base no ID
           val historicoStateAndRef = serviceHub.vaultService.queryBy<HistoricoEscolarState>(
                   QueryCriteria.LinearStateQueryCriteria(
                           linearId = listOf(UniqueIdentifier(id = historicoId))))
                   .states.single()

           // Pegamos o state retornado
           val historicoState = historicoStateAndRef.state.data

           // Validamos que não estamos fazendo nada errado.
           requireThat{
               "Eu tenho que ser a faculdade emissora do histórico para enviar para outra faculdade." using
                       (historicoState.historicoEscolar.faculdade == ourIdentity)
           }

           // Buscamos o mesmo notary utilizado anteriormente
           val notary = historicoStateAndRef.state.notary

           // Criamos o novo estado do State
           val novoHistoricoState = historicoState.copy(faculdadesReceptoras =
                       historicoState.faculdadesReceptoras + para)

           // Construimos o comando para envio
           val comando = Command(HistoricoEscolarContract.Commands.EnviarHistoricoEscolar(),
                   novoHistoricoState.participants.map { it.owningKey })

           // Contruimos a transação com os inputs e outputs
           val txBuilder = TransactionBuilder(notary)
                   .addInputState(historicoStateAndRef)
                   .addOutputState(novoHistoricoState, HistoricoEscolarContract::class.java.canonicalName)
                   .addCommand(comando)

           // Verificamos o contrato
           txBuilder.verify(serviceHub)

           // Assinamos a transação
           val transacaoParcialmenteAssinada = serviceHub.signInitialTransaction(txBuilder)

           // Contruímos as sessões para conversar com as demais faculdades
           val listaSessao = novoHistoricoState.faculdadesReceptoras.map { initiateFlow(it) }

           // Coletamos as suas assinaturas
           val transacaoTotalmenteAssinada = subFlow(CollectSignaturesFlow(
                   transacaoParcialmenteAssinada,
                   listaSessao))

           //Finalizamos a transação
           return subFlow(FinalityFlow(transacaoTotalmenteAssinada))

       }

   }

   @InitiatedBy(ReqFlow::class)
   class RespFlow(val session: FlowSession): FlowLogic<SignedTransaction>() {

       @Suspendable
       override fun call(): SignedTransaction {
           val signTransactionFlow = object : SignTransactionFlow(session) {
               override fun checkTransaction(stx: SignedTransaction) =
                       requireThat {
                   val outputs = stx.coreTransaction.outputsOfType<HistoricoEscolarState>()
                   "Tinha que ter recebido um Histórico Escolar!" using outputs.isNotEmpty()
                   "As disciplinas não podem ser emitidas no meu nome." using outputs.all { it.historicoEscolar.faculdade != ourIdentity }
               }
           }

           return subFlow(signTransactionFlow)
       }

   }

}
