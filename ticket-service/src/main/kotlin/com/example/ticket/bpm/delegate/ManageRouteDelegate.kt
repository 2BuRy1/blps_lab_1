package com.example.ticket.bpm.delegate

import com.example.ticket.api.UpdateRouteManageRequest
import com.example.ticket.service.TicketProcessService
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

@Component("manageRouteDelegate")
class ManageRouteDelegate(
    private val ticketProcessService: TicketProcessService,
) : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        val routeId = BpmVariables.requiredString(execution, "routeId")
        val request = UpdateRouteManageRequest(
            from = BpmVariables.optionalString(execution, "from"),
            to = BpmVariables.optionalString(execution, "to"),
            date = BpmVariables.optionalString(execution, "date"),
            fromTerminal = BpmVariables.optionalString(execution, "fromTerminal"),
            toTerminal = BpmVariables.optionalString(execution, "toTerminal"),
            trainId = BpmVariables.optionalString(execution, "trainId"),
            departureTime = BpmVariables.optionalString(execution, "departureTime"),
            price = BpmVariables.optionalInt(execution, "price"),
            capacity = BpmVariables.optionalInt(execution, "capacity"),
            freeSeats = BpmVariables.optionalInt(execution, "freeSeats"),
        )
        val response = ticketProcessService.updateRouteForManageInternal(routeId, request)
        execution.setVariable("reservedSeats", response.reservedSeats)
        execution.setVariable("freeSeats", response.freeSeats)
    }
}
