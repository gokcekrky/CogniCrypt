/********************************************************************************
 * Copyright (c) 2015-2019 TU Darmstadt, Paderborn University
 * 
 * http://www.eclipse.org/legal/epl-2.0. SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

package de.cognicrypt.core;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "de.cognicrypt.core"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;

	/**
	 * The constructor
	 */
	public Activator() {}

	@Override
	public void start(final BundleContext context) throws Exception {
		super.start(context);
		Activator.plugin = this;
	}

	@Override
	public void stop(final BundleContext context) throws Exception {
		Activator.plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return Activator.plugin;
	}

	private void log(final int severity, final String message, final Exception ex) {
		getLog().log(new Status(severity, Activator.PLUGIN_ID, message, ex));
	}

	public void logError(final Exception ex) {
		logError(ex, ex.getMessage());
	}

	public void logError(final Exception ex, final String message) {
		log(IStatus.ERROR, message, ex);
	}

	public void logError(final String message) {
		log(IStatus.ERROR, message, null);
	}

	public void logInfo(final String message) {
		log(IStatus.INFO, message, null);
	}

}
