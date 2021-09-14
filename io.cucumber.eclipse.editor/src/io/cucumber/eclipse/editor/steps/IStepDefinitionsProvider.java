package io.cucumber.eclipse.editor.steps;

import java.util.Collection;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.ITextViewer;

public interface IStepDefinitionsProvider {

	public static final String PROVIDER_NAME = "name";

	/**
	 * Find step definitions into a file.
	 * 
	 * @param viewer
	 * @param offset
	 * @param resource
	 * 
	 * @param stepDefinitionResource the file where to search step definition.
	 * @param markerFactory          factory of markers supported by this plugin
	 * @param monitor                the progress monitor
	 * @return a set of StepDefinition or an empty array. Should NEVER return null.
	 * @throws CoreException if an error occurs
	 */
	Collection<StepDefinition> findStepDefinitions(ITextViewer viewer, int offset, IResource resource,
			IProgressMonitor monitor)
			throws CoreException;
	
	/**
	 * Indicate if the step definition provider is able to 
	 * parse the resource to scan step definitions. 
	 * @param resource a resource potentially a step definitions source
	 * @return true if the file could be analyzed
	 * @throws CoreException if an error occurs
	 */
	boolean support(IResource resource) throws CoreException;

}
