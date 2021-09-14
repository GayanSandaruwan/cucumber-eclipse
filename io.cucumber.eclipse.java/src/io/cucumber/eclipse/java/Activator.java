package io.cucumber.eclipse.java;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "io.cucumber.eclipse.java"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;

	/**
	 * The constructor
	 */
	public Activator() {
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		// FIXME das gibt probleme
//		IPreferenceStore store = getPreferenceStore();
//		store.setDefault(CucumberJavaPreferences.PREF_SHOW_HOOK_ANNOTATIONS, true);
//		store.addPropertyChangeListener(new CucumberJavaPreferencesChangeListener());
//		JavaCore.addElementChangedListener(new IElementChangedListener() {
//
//			@Override
//			public void elementChanged(ElementChangedEvent event) {
//				IJavaElementDelta delta = event.getDelta();
//				System.out.println("Element changed: " + delta.getElement());
//			}
//		});
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

	public static void warn(String string, Throwable e) {
		getDefault().getLog().warn(string, e);

	}

	public static void error(String string, Throwable e) {
		getDefault().getLog().warn(string, e);

	}

}
