package com.example.ticket.bpm.listener;

import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.task.TaskDefinition;
import org.camunda.bpm.engine.impl.util.xml.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EnterRouteTaskListenerPlugin extends AbstractProcessEnginePlugin {
    private static final String TASK_ID = "Task_Client_EnterRoute";

    @Override
    public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
        List<BpmnParseListener> listeners = processEngineConfiguration.getCustomPostBPMNParseListeners();
        if (listeners == null) {
            listeners = new ArrayList<>();
            processEngineConfiguration.setCustomPostBPMNParseListeners(listeners);
        }
        listeners.add(new EnterRouteParseListener());
    }

    private static class EnterRouteParseListener extends AbstractBpmnParseListener {
        @Override
        public void parseUserTask(Element userTaskElement, ScopeImpl scope, ActivityImpl activity) {
            if (!TASK_ID.equals(activity.getId())) {
                return;
            }
            ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) scope.getProcessDefinition();
            TaskDefinition taskDefinition = processDefinition.getTaskDefinitions().get(activity.getId());
            if (taskDefinition != null) {
                taskDefinition.addTaskListener(TaskListener.EVENTNAME_COMPLETE, new EnterRouteTaskListener());
            }
        }
    }
}
