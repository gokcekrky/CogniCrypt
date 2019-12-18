/********************************************************************************
 * Copyright (c) 2015-2019 TU Darmstadt, Paderborn University
 * 

 * http://www.eclipse.org/legal/epl-2.0. SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

package de.cognicrypt.crysl.reader;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.xtext.common.types.access.impl.ClasspathTypeProvider;
import org.eclipse.xtext.common.types.access.jdt.IJavaProjectProvider;
import org.eclipse.xtext.common.types.access.jdt.JdtTypeProviderFactory;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.common.io.Files;
import com.google.inject.Injector;

import de.cognicrypt.core.Constants;
import de.cognicrypt.crysl.handler.Activator;
import de.cognicrypt.utils.Utils;
import de.darmstadt.tu.crossing.CrySLStandaloneSetup;
import de.darmstadt.tu.crossing.CrySL.ui.internal.CrySLActivator;
import de.darmstadt.tu.crossing.reader.CrySLModelReader;
import de.darmstadt.tu.crossing.rules.CrySLRule;

public class CrySLPluginModelReader extends CrySLModelReader {

	private boolean testMode = false;

	public CrySLPluginModelReader(IProject iProject) throws CoreException, IOException {
		final Injector injector = CrySLActivator.getInstance()
				.getInjector(CrySLActivator.DE_DARMSTADT_TU_CROSSING_CRYSL);
		resourceSet = injector.getInstance(XtextResourceSet.class);

		if (iProject == null) {
			// if no project selected abort with error message
			iProject = Utils.complileListOfJavaProjectsInWorkspace().get(0);
		}
		if (iProject.isOpen()) {
			resourceSet.setClasspathURIContext(JavaCore.create(iProject));
		}
		new JdtTypeProviderFactory(injector.getInstance(IJavaProjectProvider.class)).createTypeProvider(resourceSet);

		resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
	}

	/**
	 * This constructor use the CogniCyrpt Core plugin lib folder as classpath
	 */
	public CrySLPluginModelReader() throws MalformedURLException {
		CrySLStandaloneSetup crySLStandaloneSetup = new CrySLStandaloneSetup();
		final Injector injector = crySLStandaloneSetup.createInjectorAndDoEMFRegistration();
		this.resourceSet = injector.getInstance(XtextResourceSet.class);
		String coreLibFolderPath = Utils.getResourceFromWithin("lib").getAbsolutePath();

		List<File> jars = new ArrayList<>();
		for (String file : Utils.getResourceFromWithin("lib").list()) {
			jars.add(new File(coreLibFolderPath + Constants.innerFileSeparator + file));
		}

		URL[] classpath = new URL[jars.size()];
		for (int i = 0; i < classpath.length; i++) {
			try {
				classpath[i] = jars.get(i).toURI().toURL();
			} catch (MalformedURLException e) {
				Activator.getDefault()
						.logError("File path: " + jars.get(i) + " could not converted to java.net.URI object");
			}
		}

		URLClassLoader ucl = new URLClassLoader(classpath);
		this.resourceSet.setClasspathURIContext(new URLClassLoader(classpath));
		new ClasspathTypeProvider(ucl, this.resourceSet, null, null);
		this.resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);

		testMode = true;
	}
	
	public List<CrySLRule> readRulesWithin(String resourcesPath) throws CoreException {
		final IPath rulesFolder = (new org.eclipse.core.runtime.Path(resourcesPath));
		List<CrySLRule> rules = new ArrayList<CrySLRule>();
		if (rulesFolder.segmentCount() == 1) {
			return rules;
		}
		for (final IResource res : ResourcesPlugin.getWorkspace().getRoot().getFolder(rulesFolder).members()) {
			if (Constants.cryslFileEnding.equals("." + res.getFileExtension())) {
				File resAsFile = ((IFile) res).getRawLocation().makeAbsolute().toFile();
				CrySLRule rule = readRule(resAsFile);
				if (rule != null) {
					rules.add(rule);
					File to = new File(Utils.getResourceFromWithin(Constants.RELATIVE_CUSTOM_RULES_DIR, de.cognicrypt.core.Activator.PLUGIN_ID).getAbsolutePath() + Constants.innerFileSeparator
							+ rule.getClassName().substring(rule.getClassName().lastIndexOf(".") + 1) + Constants.cryslFileEnding);
					try {
						Files.copy(resAsFile, to);
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				}
			} else if (res instanceof IFolder) {
				rules.addAll(readRulesWithin(res.getFullPath().toOSString()));
			}
		}
		return rules;
	}
	
	public List<CrySLRule> readRulesOutside(String resourcesPath) throws CoreException {
		List<CrySLRule> rules = new ArrayList<CrySLRule>();
		for (File a : ((new File(resourcesPath)).listFiles())) {
			if (!a.isDirectory() && a.exists() && a.canRead()) {
				CrySLRule rule = readRule(a);
				if (rule != null) {
					rules.add(rule);
				}
			}
		}

		return rules;
	}
}
