package com.example.ticket.bpm.listener;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class EnterRouteTaskListener implements TaskListener {
    private static final String ERROR_CODE = "VALIDATION_ERROR";
    private static final String VALIDATION_ERROR_VARIABLE = "validationError";
    private static final String FORM_ERROR_VARIABLE = "validateError";

    @Override
    public void notify(DelegateTask delegateTask) {
        List<String> errors = new ArrayList<>();

        String from = requiredString(delegateTask, "from", errors);
        String to = requiredString(delegateTask, "to", errors);
        String travelDate = requiredString(delegateTask, "travelDate", errors);

        if (from != null && to != null && from.equalsIgnoreCase(to)) {
            errors.add("to: must be different from from");
        }

        if (travelDate != null) {
            try {
                LocalDate.parse(travelDate);
            } catch (DateTimeParseException ignored) {
                errors.add("travelDate: must be in YYYY-MM-DD format");
            }
        }

        if (!errors.isEmpty()) {
            String message = String.join("; ", errors);
            delegateTask.setVariable(VALIDATION_ERROR_VARIABLE, message);
            delegateTask.setVariable(FORM_ERROR_VARIABLE, message);
            throw new BpmnError(ERROR_CODE, message);
        }

        delegateTask.setVariable(VALIDATION_ERROR_VARIABLE, "");
        delegateTask.setVariable(FORM_ERROR_VARIABLE, "");
    }

    private String requiredString(DelegateTask delegateTask, String name, List<String> errors) {
        Object value = delegateTask.getVariable(name);
        if (!(value instanceof String rawValue) || rawValue.isBlank()) {
            errors.add(name + ": must be provided");
            return null;
        }
        return rawValue.trim();
    }
}
