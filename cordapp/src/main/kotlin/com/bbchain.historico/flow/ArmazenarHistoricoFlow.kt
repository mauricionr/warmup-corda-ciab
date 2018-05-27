package com.bbchain.historico.flow

import co.paralleluniverse.fibers.Suspendable
import com.bbchain.historico.contract.HistoricoEscolarContract
import com.bbchain.historico.model.HistoricoEscolar
import com.bbchain.historico.state.HistoricoEscolarState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

object ArmazenarHistoricoFlow {

   class ReqFlow(val historicoEscolar: HistoricoEscolar): FlowLogic<SignedTransaction>(){

       @Suspendable
       override fun call(): SignedTransaction {
           // Valida regras especificas do nó
           requireThat{
               "Eu tenho que ser a faculdade emissora." using
                       (historicoEscolar.faculdade == ourIdentity)
           }

           //Identifica o notary
           val notary = serviceHub.networkMapCache.notaryIdentities.first()

           //Constroi o State
           val disciplinaState = HistoricoEscolarState(historicoEscolar)

           // Especifica o comando e quem irá assinar o comando
           val comando = Command(
                   HistoricoEscolarContract.Commands.CriarHistoricoEscolar(),
                   disciplinaState.participants.map { it.owningKey })

           // Constroi a transação
           val txBuilder = TransactionBuilder(notary)
               .addOutputState(disciplinaState, HistoricoEscolarContract::class.java.canonicalName)
               .addCommand(comando)
           // Valida todos os contratos envolvidos na transação
           txBuilder.verify(serviceHub)

           // Assina a transação
           val transacaoAssinada = serviceHub.signInitialTransaction(txBuilder)

           // Finaliza a transação
           // O FinalityFlow é um flow padrão do Corda que irá verificar se
           // todos que precisavam assinar a transação a assinaram, envia para
           // o Notary especificado na transação validar, e após a conclusão
           // sinaliza para todas as partes que já pode salvar o novo state
           return subFlow(FinalityFlow(transacaoAssinada))
       }

   }

}
