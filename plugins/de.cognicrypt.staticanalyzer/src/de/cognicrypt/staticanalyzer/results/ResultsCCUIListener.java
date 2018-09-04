/********************************************************************************
 * Copyright (c) 2015-2018 TU Darmstadt
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

package de.cognicrypt.staticanalyzer.results;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import boomerang.BackwardQuery;
import boomerang.Query;
import boomerang.jimple.Statement;
import boomerang.jimple.Val;
import boomerang.results.ForwardBoomerangResults;
import crypto.analysis.AnalysisSeedWithSpecification;
import crypto.analysis.CrySLAnalysisListener;
import crypto.analysis.EnsuredCryptSLPredicate;
import crypto.analysis.IAnalysisSeed;
import crypto.analysis.errors.AbstractError;
import crypto.analysis.errors.ImpreciseValueExtractionError;
import crypto.extractparameter.CallSiteWithParamIndex;
import crypto.extractparameter.ExtractedValue;
import crypto.interfaces.ISLConstraint;
import crypto.rules.CryptSLPredicate;
import de.cognicrypt.staticanalyzer.Activator;
import de.cognicrypt.staticanalyzer.statment.CCStatement;
import de.cognicrypt.utils.Utils;
import de.cognicrypt.utils.XMLParser;
import soot.SootClass;
import typestate.TransitionFunction;

/**
 * This listener is notified of any misuses the analysis finds.
 *
 * @author Stefan Krueger
 * @author André Sonntag
 *
 */
public class ResultsCCUIListener extends CrySLAnalysisListener {

	private final ErrorMarkerGenerator markerGenerator;
	private final IProject currentProject;
	private ArrayList<String> suppressedWarningIds;
	private String warningFilePath;
	private XMLParser xmlParser;

	private ResultsCCUIListener(final IProject curProj, final ErrorMarkerGenerator gen) {
		this.currentProject = curProj;
		this.markerGenerator = gen;
		this.suppressedWarningIds = new ArrayList<>();
	}

	public static ResultsCCUIListener createListener(IProject project) {
		ResultsCCUIListener listener = new ResultsCCUIListener(project, new ErrorMarkerGenerator());
		Activator.registerResultsListener(listener);
		return listener;
	}

	/**
	 * @return the currentProject
	 */
	public IProject getReporterProject() {
		return currentProject;
	}

	@Override
	public void reportError(AbstractError error) {
		String errorMessage = error.toErrorMarkerString();
		Statement errorLocation = error.getErrorLocation();
		IResource sourceFile = unitToResource(errorLocation);
		int lineNumber = errorLocation.getUnit().get().getJavaSourceStartLineNumber();
		CCStatement stmt = new CCStatement(errorLocation);
		int stmtId = stmt.hashCode();
		String stmtVar = stmt.getVar();

		warningFilePath = sourceFile.getProject().getLocation().toOSString() + "\\SuppressWarnings.xml";
		File warningsFile = new File(warningFilePath);

		if (!warningsFile.exists()) {
			if (error instanceof ImpreciseValueExtractionError) {
				this.markerGenerator.addMarker(stmtId, sourceFile, lineNumber, stmtVar, errorMessage, true);
			} else {
				this.markerGenerator.addMarker(stmtId, sourceFile, lineNumber, stmtVar, errorMessage);
			}
		} else {
			xmlParser = new XMLParser(warningsFile);
			xmlParser.useDocFromFile();
			if (!xmlParser.getAttrValuesByAttrName("SuppressWarning", "ID").contains(stmtId + "")) {
				
				if (error instanceof ImpreciseValueExtractionError) {
					this.markerGenerator.addMarker(stmtId, sourceFile, lineNumber, stmtVar, errorMessage, true);
				} else {
					this.markerGenerator.addMarker(stmtId, sourceFile, lineNumber, stmtVar, errorMessage);
				}
			} else {	
				
				//update existing LineNumber
				Node suppressWarningNode = xmlParser.getNodeByAttrValue("SuppressWarning", "ID",stmtId+"");
				Node lineNumberNode = xmlParser.getChildNodeByTagName(suppressWarningNode, "LineNumber");
				xmlParser.updateNodeValue(lineNumberNode, lineNumber+"");
				xmlParser.writeXML();
				suppressedWarningIds.add(stmtId + "");
			}
		}
	}

	/**
	 * This method removes superfluous suppressed warning entries from the
	 * SuppressWarnings.xml file.
	 */
	public void removeUndetectableWarnings() {
		if (suppressedWarningIds.size() > 0) {

			ArrayList<String> allSuppressedWarningIds = xmlParser.getAttrValuesByAttrName("SuppressWarning", "ID");

			ArrayList<String> difference = new ArrayList<>(allSuppressedWarningIds.size());
			difference.addAll(allSuppressedWarningIds);
			difference.removeAll(suppressedWarningIds);

			for (int i = 0; i < difference.size(); i++) {
				xmlParser.removeNodeByAttrValue("SuppressWarning", "ID", difference.get(i));
			}
			xmlParser.writeXML();

		}
		suppressedWarningIds = new ArrayList<>();
	}

	private IResource unitToResource(final Statement stmt) {
		final SootClass className = stmt.getMethod().getDeclaringClass();
		try {
			return Utils.findClassByName(className.getName(), this.currentProject);
		} catch (final ClassNotFoundException e) {
			Activator.getDefault().logError(e);
		}
		// Fall-back path when retrieval of actual path fails. If the statement below
		// fails, it should be left untouched as the actual bug is above.
		return this.currentProject.getFile("src/" + className.getName().replace(".", "/") + ".java");
	}

	@Override
	public void checkedConstraints(final AnalysisSeedWithSpecification arg0, final Collection<ISLConstraint> arg1) {
		// Nothing
	}

	@Override
	public void discoveredSeed(final IAnalysisSeed arg0) {
		// Nothing
	}

	@Override
	public void onSeedTimeout(final sync.pds.solver.nodes.Node<Statement, Val> arg0) {
		// Nothing
	}

	@Override
	public void collectedValues(AnalysisSeedWithSpecification arg0,
			Multimap<CallSiteWithParamIndex, ExtractedValue> arg1) {
		// Nothing
	}

	@Override
	public void onSeedFinished(IAnalysisSeed arg0, ForwardBoomerangResults<TransitionFunction> arg1) {
		// Nothing
	}

	public ErrorMarkerGenerator getMarkerGenerator() {
		return markerGenerator;
	}

	@Override
	public void beforeAnalysis() {
		// TODO Auto-generated method stub

	}

	@Override
	public void afterAnalysis() {
		removeUndetectableWarnings();
	}

	@Override
	public void beforeConstraintCheck(AnalysisSeedWithSpecification analysisSeedWithSpecification) {
		// TODO Auto-generated method stub

	}

	@Override
	public void afterConstraintCheck(AnalysisSeedWithSpecification analysisSeedWithSpecification) {
		// TODO Auto-generated method stub

	}

	@Override
	public void beforePredicateCheck(AnalysisSeedWithSpecification analysisSeedWithSpecification) {
		// TODO Auto-generated method stub

	}

	@Override
	public void afterPredicateCheck(AnalysisSeedWithSpecification analysisSeedWithSpecification) {
		// TODO Auto-generated method stub

	}

	@Override
	public void seedStarted(IAnalysisSeed analysisSeedWithSpecification) {
		// TODO Auto-generated method stub

	}

	@Override
	public void boomerangQueryStarted(Query seed, BackwardQuery q) {
		// TODO Auto-generated method stub

	}

	@Override
	public void boomerangQueryFinished(Query seed, BackwardQuery q) {
		// TODO Auto-generated method stub

	}

	@Override
	public void ensuredPredicates(Table<Statement, Val, Set<EnsuredCryptSLPredicate>> existingPredicates,
			Table<Statement, IAnalysisSeed, Set<CryptSLPredicate>> expectedPredicates,
			Table<Statement, IAnalysisSeed, Set<CryptSLPredicate>> missingPredicates) {
		// TODO Auto-generated method stub

	}

}
