package com.bbchain.historico.contract

import com.bbchain.historico.state.HistoricoEscolarState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class HistoricoEscolarContract : Contract {

   override fun verify(tx: LedgerTransaction) {
       val command = tx.commandsOfType<Commands>().single()
       when(command.value){
           is Commands.CriarHistoricoEscolar -> verifyCriarHistoricoEscolar(tx)
           is Commands.EnviarHistoricoEscolar -> verifyEnviarHistoricoEscolar(tx)
           else -> throw IllegalStateException("Não reconheço este comando.")
       }

       val output = tx.outputsOfType<HistoricoEscolarState>().single()
        requireThat {
            "Todos envolvidos devem assinar" using 
                command.signers.containsAll((output.participants).map { it.owningKey })
        }
   }

    fun verifyCriarHistoricoEscolar(tx: LedgerTransaction){
        requireThat {
            // Regras de entradas
            "Tem que ter um e apenas um Output." using (tx.outputsOfType<HistoricoEscolarState>().size == 1)
            "Não pode haver Input." using (tx.inputsOfType<HistoricoEscolarState>().isEmpty())

            val outputs = tx.outputsOfType<HistoricoEscolarState>().single()
            // Regras de criacao de HistoricoEscolar
            "É necessário que tenha um id de aluno." using outputs.all { it.historicoEscolar.idAluno > 0 }
            "A nota não pode ser negativa." using outputs.none { it.historicoEscolar.nota < 0 }
        }
    }

    fun verifyEnviarHistoricoEscolar(tx: LedgerTransaction){
        requireThat {
            "Tem que ter um e apenas um input." using (tx.inputsOfType<HistoricoEscolarState>().size == 1)
            "Tem que ter um e apenas um output." using (tx.outputsOfType<HistoricoEscolarState>().size == 1)

            val input = tx.inputsOfType<HistoricoEscolarState>().single()
            val output = tx.outputsOfType<HistoricoEscolarState>().single()
            // Regras de Envio de HistoricoEscolar
            "Não pode ser removida uma faculdade da lista de faculdades receptoras." using (output.faculdadesReceptoras.containsAll(input.faculdadesReceptoras))
            "A lista de faculdades receptoras precisa ter mais uma faculdade." using (
                            (output.faculdadesReceptoras.size == input.faculdadesReceptoras.size + 1))
            "A historicoEscolar não pode ser alterada." using (output.historicoEscolar == input.historicoEscolar)
        }
    }

        interface Commands: CommandData {
        class CriarHistoricoEscolar : Commands
        class EnviarHistoricoEscolar : Commands
    }
}

