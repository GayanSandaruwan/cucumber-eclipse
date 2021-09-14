package io.cucumber.eclipse.java.plugins;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.Plugin;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.StepDefinedEvent;
import io.cucumber.plugin.event.StepDefinition;

/**
 * Cucumber plugin that collects all steps registered during a cucumber run
 * 
 * @author christoph
 *
 */
public class CucumberStepParserPlugin implements Plugin, ConcurrentEventListener, EventListener {

	private Map<CucumberCodeLocation, CucumberStepDefinition> stepList = new ConcurrentHashMap<>();

	@Override
	public void setEventPublisher(EventPublisher publisher) {
		publisher.registerHandlerFor(StepDefinedEvent.class, this::handleStepDefinedEvent);
	}

	private void handleStepDefinedEvent(StepDefinedEvent event) {
		StepDefinition definition = event.getStepDefinition();
		stepList.computeIfAbsent(new CucumberCodeLocation(definition.getLocation()),
				location -> new CucumberStepDefinition(definition, location));
		// TODO it seems the Envelope is much more descriptive:
		// but it is missing the location in the document!
//		step_definition {
//			  id: "f1f717da-66ac-4fe6-82e0-a0117df31d34"
//			  pattern {
//			    source: "the previous entries:"
//			  }
//			  source_reference {
//			    java_method {
//			      class_name: "io.cucumber.examples.java.RpnCalculatorSteps"
//			      method_name: "thePreviousEntries"
//			      method_parameter_types: "java.util.List"
//			    }
//			  }
//			}
	}

	/**
	 * @return a Map of locations to recorded step definitions
	 */
	public Collection<CucumberStepDefinition> getStepList() {
		return stepList.values();
	}



}
