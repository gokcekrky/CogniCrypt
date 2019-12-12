/********************************************************************************
 * Copyright (c) 2015-2019 TU Darmstadt, Paderborn University
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

package de.cognicrypt.codegenerator.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import de.darmstadt.tu.crossing.crysl.interfaces.ISLConstraint;
import de.darmstadt.tu.crossing.crysl.rules.CrySLForbiddenMethod;
import de.darmstadt.tu.crossing.crysl.rules.CrySLMethod;
import de.darmstadt.tu.crossing.crysl.rules.CrySLObject;
import de.darmstadt.tu.crossing.crysl.rules.CrySLPredicate;
import de.darmstadt.tu.crossing.crysl.rules.CrySLRule;
import de.darmstadt.tu.crossing.crysl.rules.StateMachineGraph;

public class CodeGenCrySLRule extends CrySLRule {

	private static final long serialVersionUID = -7488186084564628280L;
	private List<CrySLMethod> requiredMethods;
	private List<CodeGenCrySLObject> requiredPars;
	private CrySLObject requiredRetObj;

	public CodeGenCrySLRule(String _className, List<Entry<String, String>> defObjects, List<CrySLForbiddenMethod> _forbiddenMethods, StateMachineGraph _usagePattern, List<ISLConstraint> _constraints, List<CrySLPredicate> _predicates, List<CodeGenCrySLObject> pars, CrySLObject reqRet) {
		super(_className, defObjects, _forbiddenMethods, _usagePattern, _constraints, _predicates);
		requiredPars = pars;
		requiredRetObj = reqRet;
	}

	public CodeGenCrySLRule(CrySLRule rule, List<CodeGenCrySLObject> pars, CrySLObject reqRet) {
		this(rule.getClassName(), rule.getObjects(), rule.getForbiddenMethods(), rule.getUsagePattern(), rule.getConstraints(), rule.getPredicates(), pars, reqRet);
	}

	public boolean addConstraint(ISLConstraint constraint) {
		return constraints.add(constraint);
	}

	public boolean addForbiddenMethod(CrySLMethod method) {
		return forbiddenMethods.add(new CrySLForbiddenMethod(method, false));
	}

	public boolean addRequiredMethod(CrySLMethod method) {
		if (requiredMethods == null) {
			requiredMethods = new ArrayList<CrySLMethod>();
		}
		return requiredMethods.add(method);
	}

	public List<CodeGenCrySLObject> getRequiredPars() {
		return requiredPars;
	}

	public CrySLObject getRequiredRetObj() {
		return requiredRetObj;
	}

}
