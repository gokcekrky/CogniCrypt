/********************************************************************************
 * Copyright (c) 2015-2018 TU Darmstadt
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

package de.cognicrypt.crysl.reader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.xtext.common.types.JvmExecutable;
import org.eclipse.xtext.common.types.JvmFormalParameter;
import org.eclipse.xtext.common.types.access.jdt.IJavaProjectProvider;
import org.eclipse.xtext.common.types.access.jdt.JdtTypeProviderFactory;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Injector;

import crypto.interfaces.ICryptSLPredicateParameter;
import crypto.interfaces.ISLConstraint;
import crypto.rules.CryptSLArithmeticConstraint;
import crypto.rules.CryptSLArithmeticConstraint.ArithOp;
import crypto.rules.CryptSLComparisonConstraint;
import crypto.rules.CryptSLComparisonConstraint.CompOp;
import crypto.rules.CryptSLCondPredicate;
import crypto.rules.CryptSLConstraint;
import crypto.rules.CryptSLConstraint.LogOps;
import crypto.rules.CryptSLForbiddenMethod;
import crypto.rules.CryptSLMethod;
import crypto.rules.CryptSLObject;
import crypto.rules.CryptSLPredicate;
import crypto.rules.CryptSLRule;
import crypto.rules.CryptSLSplitter;
import crypto.rules.CryptSLValueConstraint;
import crypto.rules.ParEqualsPredicate;
import crypto.rules.StateMachineGraph;
import crypto.rules.StateNode;
import crypto.rules.TransitionEdge;
import de.cognicrypt.core.Constants;
import de.cognicrypt.crysl.handler.Activator;
import de.cognicrypt.utils.Utils;
import de.darmstadt.tu.crossing.CryptSL.ui.internal.CryptSLActivator;
import de.darmstadt.tu.crossing.cryptSL.ArithmeticExpression;
import de.darmstadt.tu.crossing.cryptSL.ArithmeticOperator;
import de.darmstadt.tu.crossing.cryptSL.ArrayElements;
import de.darmstadt.tu.crossing.cryptSL.ComparisonExpression;
import de.darmstadt.tu.crossing.cryptSL.Constraint;
import de.darmstadt.tu.crossing.cryptSL.DestroysBlock;
import de.darmstadt.tu.crossing.cryptSL.Domainmodel;
import de.darmstadt.tu.crossing.cryptSL.EnsuresBlock;
import de.darmstadt.tu.crossing.cryptSL.Event;
import de.darmstadt.tu.crossing.cryptSL.Expression;
import de.darmstadt.tu.crossing.cryptSL.ForbMethod;
import de.darmstadt.tu.crossing.cryptSL.ForbiddenBlock;
import de.darmstadt.tu.crossing.cryptSL.Literal;
import de.darmstadt.tu.crossing.cryptSL.LiteralExpression;
import de.darmstadt.tu.crossing.cryptSL.Object;
import de.darmstadt.tu.crossing.cryptSL.ObjectDecl;
import de.darmstadt.tu.crossing.cryptSL.PreDefinedPredicates;
import de.darmstadt.tu.crossing.cryptSL.Pred;
import de.darmstadt.tu.crossing.cryptSL.ReqPred;
import de.darmstadt.tu.crossing.cryptSL.SuPar;
import de.darmstadt.tu.crossing.cryptSL.SuParList;
import de.darmstadt.tu.crossing.cryptSL.SuperType;
import de.darmstadt.tu.crossing.cryptSL.UnaryPreExpression;
import de.darmstadt.tu.crossing.cryptSL.UseBlock;
import de.darmstadt.tu.crossing.cryptSL.impl.ObjectImpl;

public class CrySLModelReader {

	private List<CryptSLForbiddenMethod> forbiddenMethods = null;
	private StateMachineGraph smg = null;
	private String curClass = "";

	public CrySLModelReader(final IResource crySLFile) throws ClassNotFoundException, CoreException, IOException {
		final Injector injector = CryptSLActivator.getInstance()
				.getInjector(CryptSLActivator.DE_DARMSTADT_TU_CROSSING_CRYPTSL);
		final XtextResourceSet resourceSet = injector.getInstance(XtextResourceSet.class);

		IProject iproject = crySLFile.getProject();
		if (iproject == null) {
			// if no project selected abort with error message
			iproject = Utils.complileListOfJavaProjectsInWorkspace().get(0);
		}
		if (iproject.isOpen()) {
			resourceSet.setClasspathURIContext(JavaCore.create(iproject));
		}
		new JdtTypeProviderFactory(injector.getInstance(IJavaProjectProvider.class)).createTypeProvider(resourceSet);

		resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
		final List<String> exceptions = new ArrayList<>();
		exceptions.add("String.cryptsl");

		final IPath rulesFolder = crySLFile.getFullPath().removeLastSegments(1);
		for (final IResource res : ResourcesPlugin.getWorkspace().getRoot().getFolder(rulesFolder).members()) {
			final String extension = res.getFileExtension();
			final String fileName = res.getName();
			if (!"cryptsl".equals(extension) || exceptions.contains(fileName)) { // || !fileName.contains("Signature."))
																					// {
				continue;
			}
			final Resource resource = resourceSet.getResource(URI.createPlatformResourceURI(
					rulesFolder.toPortableString() + Constants.outerFileSeparator + fileName, true), true);
			EcoreUtil.resolveAll(resourceSet);
			final EObject eObject = resource.getContents().get(0);
			final Domainmodel dm = (Domainmodel) eObject;
			curClass = dm.getJavaType().getQualifiedName();
			final EnsuresBlock ensure = dm.getEnsure();
			final Map<ParEqualsPredicate, SuperType> pre_preds = Maps.newHashMap();
			final DestroysBlock destroys = dm.getDestroy();
			if (destroys != null) {
				pre_preds.putAll(getKills(destroys.getPred()));
			}
			if (ensure != null) {
				pre_preds.putAll(getPredicates(ensure.getPred()));
			}

			this.smg = buildStateMachineGraph(dm.getOrder());
			final ForbiddenBlock forbEvent = dm.getForbEvent();
			this.forbiddenMethods = (forbEvent != null) ? getForbiddenMethods(forbEvent.getForb_methods())
					: Lists.newArrayList();

			final List<ISLConstraint> constraints = (dm.getReqConstraints() != null)
					? buildUpConstraints(dm.getReqConstraints().getReq())
					: Lists.newArrayList();
			constraints.addAll(((dm.getRequire() != null) ? collectRequiredPredicates(dm.getRequire().getPred())
					: Lists.newArrayList()));
			final List<Entry<String, String>> objects = getObjects(dm.getUsage());

			final List<CryptSLPredicate> actPreds = Lists.newArrayList();

			for (final ParEqualsPredicate pred : pre_preds.keySet()) {
				final SuperType cond = pre_preds.get(pred);
				if (cond == null) {
					actPreds.add(pred.tobasicPredicate());
				} else {
					actPreds.add(new CryptSLCondPredicate(pred.getBaseObject(), pred.getPredName(),
							pred.getParameters(), pred.isNegated(),
							getStatesForMethods(CrySLReaderUtils.resolveAggregateToMethodeNames(cond))));
				}
			}
			final CryptSLRule rule = new CryptSLRule(curClass, objects, this.forbiddenMethods, this.smg, constraints,
					actPreds);
			System.out.println(rule);
			System.out.println("===========================================");
			System.out.println("");

			final String className = fileName.substring(0, fileName.indexOf(extension) - 1);
			storeRuletoFile(rule,
					Utils.getResourceFromWithin("resources/CrySLRules", de.cognicrypt.core.Activator.PLUGIN_ID)
							.getAbsolutePath(),
					className);

			final String filePath = "C:\\Users\\stefank3\\git\\CryptoAnalysis\\CryptoAnalysis\\src\\test\\resources\\";
			if ((new File(filePath)).exists()) {
				storeRuletoFile(rule, filePath, className);
			}
		}

	}

	private StateMachineGraph buildStateMachineGraph(final Expression order) {
		final StateMachineGraphBuilder smgb = new StateMachineGraphBuilder(order);
		return smgb.buildSMG();
	}

	private List<ISLConstraint> buildUpConstraints(final List<Constraint> constraints) {
		final List<ISLConstraint> slCons = new ArrayList<>();
		for (final Constraint cons : constraints) {
			final ISLConstraint constraint = getConstraint(cons);
			if (constraint != null) {
				slCons.add(constraint);
			}
		}
		return slCons;
	}

	private List<ISLConstraint> collectRequiredPredicates(final EList<ReqPred> requiredPreds) {
		final List<ISLConstraint> preds = new ArrayList<>();
		for (final ReqPred pred : requiredPreds) {
			final Constraint conditional = pred.getCons();
			final List<ICryptSLPredicateParameter> variables = new ArrayList<>();
			if (pred.getPred().getParList() != null) {
				for (final SuPar var : pred.getPred().getParList().getParameters()) {
					if (var.getVal() != null) {
						final LiteralExpression lit = var.getVal();

						ObjectImpl object = (ObjectImpl) ((LiteralExpression) lit.getLit().getName()).getValue();
						ObjectDecl objectDecl = (ObjectDecl) object.eContainer();
						String type = objectDecl.getObjectType().getQualifiedName()
								+ ((objectDecl.getArray() != null) ? objectDecl.getArray() : "");
						String variable = object.getName();

						final String part = var.getVal().getPart();
						if (part != null) {
							variables.add(new CryptSLObject(variable, type, new CryptSLSplitter(
									Integer.parseInt(lit.getInd()), Utils.filterQuotes(lit.getSplit()))));
						} else {
							variables.add(new CryptSLObject(variable, type));
						}
					} else {
						variables.add(new CryptSLObject("_", "null"));
					}
				}
			}

			preds.add(new CryptSLPredicate(null, pred.getPred().getPredName(), variables,
					(pred.getNot() != null ? true : false), getConstraint(conditional)));
		}
		return preds;
	}

	private CryptSLArithmeticConstraint convertLiteralToArithmetic(final Constraint expression) {
		final LiteralExpression cons = (LiteralExpression) ((LiteralExpression) expression).getCons();
		ICryptSLPredicateParameter name;
		if (cons instanceof PreDefinedPredicates) {
			name = getPredefinedPredicate((LiteralExpression) expression);
		} else {
			EObject constraint = cons.getName();
			String object = getValueOfLiteral(constraint);
			if (constraint instanceof LiteralExpression) {
				ObjectDecl objectDecl = (ObjectDecl) ((ObjectImpl) ((LiteralExpression) constraint).getValue())
						.eContainer();
				name = new CryptSLObject(object, objectDecl.getObjectType().getQualifiedName()
						+ ((objectDecl.getArray() != null) ? objectDecl.getArray() : ""));
			} else {
				name = new CryptSLObject(object, "int");
			}
		}

		return new CryptSLArithmeticConstraint(name, new CryptSLObject("0", "int"),
				crypto.rules.CryptSLArithmeticConstraint.ArithOp.p);
	}

	private ISLConstraint getConstraint(final Constraint cons) {
		if (cons == null) {
			return null;
		}
		ISLConstraint slci = null;

		if (cons instanceof ArithmeticExpression) {
			final ArithmeticExpression ae = (ArithmeticExpression) cons;
			ae.getOperator().toString();
			slci = new CryptSLArithmeticConstraint(new CryptSLObject("0", "int"), new CryptSLObject("1", "int"),
					ArithOp.n);
		} else if (cons instanceof LiteralExpression) {
			final LiteralExpression lit = (LiteralExpression) cons;
			final List<String> parList = new ArrayList<>();
			if (lit.getLitsleft() != null) {
				for (final Literal a : lit.getLitsleft().getParameters()) {
					parList.add(Utils.filterQuotes(a.getVal()));
				}
			}
			if (lit.getCons() instanceof PreDefinedPredicates) {
				slci = getPredefinedPredicate(lit);
			} else {
				final String part = ((ArrayElements) lit.getCons()).getCons().getPart();
				if (part != null) {
					final LiteralExpression name = (LiteralExpression) ((ArrayElements) lit.getCons()).getCons()
							.getLit().getName();

					SuperType object = name.getValue();
					ObjectDecl objectDecl = (ObjectDecl) object.eContainer();
					final CryptSLObject variable = new CryptSLObject(object.getName(),
							objectDecl.getObjectType().getQualifiedName()
									+ ((objectDecl.getArray() != null) ? objectDecl.getArray() : ""),
							new CryptSLSplitter(Integer.parseInt(((ArrayElements) lit.getCons()).getCons().getInd()),
									Utils.filterQuotes(((ArrayElements) lit.getCons()).getCons().getSplit())));
					slci = new CryptSLValueConstraint(variable, parList);
				} else {
					LiteralExpression name = (LiteralExpression) ((ArrayElements) lit.getCons()).getCons().getName();
					if (name == null) {
						name = (LiteralExpression) ((ArrayElements) lit.getCons()).getCons().getLit().getName();
					}
					SuperType object = name.getValue();
					ObjectDecl objectDecl = (ObjectDecl) object.eContainer();
					final CryptSLObject variable = new CryptSLObject(object.getName(),
							objectDecl.getObjectType().getQualifiedName()
									+ ((objectDecl.getArray() != null) ? objectDecl.getArray() : ""));
					slci = new CryptSLValueConstraint(variable, parList);
				}
			}
		} else if (cons instanceof ComparisonExpression) {
			final ComparisonExpression comp = (ComparisonExpression) cons;
			CompOp op = null;
			switch (comp.getOperator().toString()) {
			case ">":
				op = CompOp.g;
				break;
			case "<":
				op = CompOp.l;
				break;
			case ">=":
				op = CompOp.ge;
				break;
			case "<=":
				op = CompOp.le;
				break;
			case "!=":
				op = CompOp.neq;
				break;
			default:
				op = CompOp.eq;
			}
			CryptSLArithmeticConstraint left;
			CryptSLArithmeticConstraint right;

			final Constraint leftExpression = comp.getLeftExpression();
			if (leftExpression instanceof LiteralExpression) {
				left = convertLiteralToArithmetic(leftExpression);
			} else {
				left = (CryptSLArithmeticConstraint) leftExpression;
			}

			final Constraint rightExpression = comp.getRightExpression();
			if (rightExpression instanceof LiteralExpression) {
				right = convertLiteralToArithmetic(rightExpression);
			} else {
				final ArithmeticExpression ar = (ArithmeticExpression) rightExpression;
				final String leftValue = getValueOfLiteral(ar.getLeftExpression());
				final String rightValue = getValueOfLiteral(ar.getRightExpression());

				final ArithmeticOperator aop = ((ArithmeticOperator) ar.getOperator());
				ArithOp operator = null;
				if (aop.getPLUS() != null && !aop.getPLUS().isEmpty()) {
					operator = ArithOp.p;
				} else {
					operator = ArithOp.n;
				}

				ObjectDecl leftobjectDecl = (ObjectDecl) ((LiteralExpression) ((LiteralExpression) ((LiteralExpression) ar
						.getLeftExpression()).getCons()).getName()).getValue().eContainer();
				ObjectDecl rightobjectDecl = (ObjectDecl) ((LiteralExpression) ((LiteralExpression) ((LiteralExpression) ar
						.getRightExpression()).getCons()).getName()).getValue().eContainer();
				right = new CryptSLArithmeticConstraint(
						new CryptSLObject(leftValue,
								leftobjectDecl.getObjectType().getQualifiedName()
										+ ((leftobjectDecl.getArray() != null) ? leftobjectDecl.getArray() : "")),
						new CryptSLObject(rightValue,
								rightobjectDecl.getObjectType().getQualifiedName()
										+ ((rightobjectDecl.getArray() != null) ? rightobjectDecl.getArray() : "")),
						operator);
			}
			slci = new CryptSLComparisonConstraint(left, right, op);
		} else if (cons instanceof UnaryPreExpression) {
			final UnaryPreExpression un = (UnaryPreExpression) cons;
			final List<ICryptSLPredicateParameter> vars = new ArrayList<>();
			final Pred innerPredicate = (Pred) un.getEnclosedExpression();
			if (innerPredicate.getParList() != null) {
				for (final SuPar sup : innerPredicate.getParList().getParameters()) {
					vars.add(new CryptSLObject("_", "null"));
				}
			}
			slci = new CryptSLPredicate(null, innerPredicate.getPredName(), vars, true);
		} else if (cons instanceof Pred) {
			if (((Pred) cons).getPredName() != null && !((Pred) cons).getPredName().isEmpty()) {
				final List<ICryptSLPredicateParameter> vars = new ArrayList<>();

				final SuParList parList = ((Pred) cons).getParList();
				if (parList != null) {
					for (final SuPar sup : parList.getParameters()) {
						vars.add(new CryptSLObject("_", "null"));
					}
				}
				slci = new CryptSLPredicate(null, ((Pred) cons).getPredName(), vars, false);
			}
		} else if (cons instanceof Constraint) {
			LogOps op = null;
			switch (cons.getOperator().toString()) {
			case "&&":
				op = LogOps.and;
				break;
			case "||":
				op = LogOps.or;
				break;
			case "=>":
				op = LogOps.implies;
				break;
			case "<=>":
				op = LogOps.eq;
				break;
			default:
				op = LogOps.and;
			}
			slci = new CryptSLConstraint(getConstraint(cons.getLeftExpression()),
					getConstraint(cons.getRightExpression()), op);

		}

		return slci;
	}

	private List<CryptSLForbiddenMethod> getForbiddenMethods(final EList<ForbMethod> methods) {
		final List<CryptSLForbiddenMethod> methodSignatures = new ArrayList<>();
		for (final ForbMethod fm : methods) {
			final JvmExecutable meth = fm.getJavaMeth();
			final List<Entry<String, String>> pars = new ArrayList<>();
			for (final JvmFormalParameter par : meth.getParameters()) {
				pars.add(new SimpleEntry<>(par.getSimpleName(), par.getParameterType().getSimpleName()));
			}
			final List<CryptSLMethod> crysl = new ArrayList<>();

			Event alternative = fm.getRep();
			if (alternative != null) {
				crysl.addAll(CrySLReaderUtils.resolveAggregateToMethodeNames(alternative));
			}
			methodSignatures.add(new CryptSLForbiddenMethod(
					new CryptSLMethod(meth.getDeclaringType().getIdentifier() + "." + meth.getSimpleName(), pars, null,
							new SimpleEntry<>("_", "AnyType")),
					false, crysl));
		}
		return methodSignatures;
	}

	private Map<? extends ParEqualsPredicate, ? extends SuperType> getKills(final EList<Constraint> eList) {
		final Map<ParEqualsPredicate, SuperType> preds = new HashMap<>();
		for (final Constraint cons : eList) {
			final Pred pred = (Pred) cons;
			final List<ICryptSLPredicateParameter> variables = new ArrayList<>();

			if (pred.getParList() != null) {
				for (final SuPar var : pred.getParList().getParameters()) {
					if (var.getVal() != null) {
						ObjectImpl object = (ObjectImpl) ((LiteralExpression) var.getVal().getLit().getName())
								.getValue();
						String name = object.getName();
						ObjectDecl objectDecl = (ObjectDecl) object.eContainer();
						String type = objectDecl.getObjectType().getQualifiedName()
								+ ((objectDecl.getArray() != null) ? objectDecl.getArray() : "");
						if (name == null) {
							name = "this";
							type = curClass;
						}
						variables.add(new CryptSLObject(name, type));
					} else {
						variables.add(new CryptSLObject("_", "null"));
					}
				}
			}
			final String meth = pred.getPredName();
			final SuperType cond = pred.getLabelCond();
			if (cond == null) {
				preds.put(new ParEqualsPredicate(null, meth, variables, true), null);
			} else {
				preds.put(new ParEqualsPredicate(null, meth, variables, true), cond);
			}

		}
		return preds;
	}

	private List<Entry<String, String>> getObjects(final UseBlock usage) {
		final List<Entry<String, String>> objects = new ArrayList<>();

		for (final ObjectDecl obj : usage.getObjects()) {
			objects.add(new SimpleEntry<>(obj.getObjectType().getIdentifier(), obj.getObjectName().getName()));
		}

		return objects;
	}

	private ISLConstraint getPredefinedPredicate(final LiteralExpression lit) {
		final String pred = ((PreDefinedPredicates) lit.getCons()).getPredName();
		ISLConstraint slci = null;
		switch (pred) {
		case "callTo":
			final List<ICryptSLPredicateParameter> methodsToBeCalled = new ArrayList<>();
			methodsToBeCalled.addAll(CrySLReaderUtils
					.resolveAggregateToMethodeNames(((PreDefinedPredicates) lit.getCons()).getObj().get(0)));
			slci = new CryptSLPredicate(null, pred, methodsToBeCalled, false);
			break;
		case "noCallTo":
			final List<ICryptSLPredicateParameter> methodsNotToBeCalled = new ArrayList<>();
			final List<CryptSLMethod> resolvedMethodNames = CrySLReaderUtils
					.resolveAggregateToMethodeNames(((PreDefinedPredicates) lit.getCons()).getObj().get(0));
			for (final CryptSLMethod csm : resolvedMethodNames) {
				this.forbiddenMethods.add(new CryptSLForbiddenMethod(csm, true));
				methodsNotToBeCalled.add(csm);
			}
			slci = new CryptSLPredicate(null, pred, methodsNotToBeCalled, false);
			break;
		case "neverTypeOf":
			final List<ICryptSLPredicateParameter> varNType = new ArrayList<>();
			Object object = (de.darmstadt.tu.crossing.cryptSL.Object) ((PreDefinedPredicates) lit.getCons()).getObj()
					.get(0);
			ObjectDecl objectDecl = (ObjectDecl) object.eContainer();
			String type = objectDecl.getObjectType().getQualifiedName()
					+ ((objectDecl.getArray() != null) ? objectDecl.getArray() : "");
			varNType.add(new CryptSLObject(object.getName(), type));
			String qualifiedName = ((PreDefinedPredicates) lit.getCons()).getType().getQualifiedName();
			varNType.add(new CryptSLObject(qualifiedName, "null"));
			slci = new CryptSLPredicate(null, pred, varNType, false);
			break;
		case "length":
			final List<ICryptSLPredicateParameter> variables = new ArrayList<>();
			Object objectL = (de.darmstadt.tu.crossing.cryptSL.Object) ((PreDefinedPredicates) lit.getCons()).getObj()
					.get(0);
			objectDecl = (ObjectDecl) objectL.eContainer();
			String typeL = objectDecl.getObjectType().getQualifiedName()
					+ ((objectDecl.getArray() != null) ? objectDecl.getArray() : "");
			variables.add(new CryptSLObject(objectL.getName(), typeL));
			slci = new CryptSLPredicate(null, pred, variables, false);
			break;
		default:
			new RuntimeException();
		}
		return slci;
	}

	private Map<? extends ParEqualsPredicate, ? extends SuperType> getPredicates(final List<Constraint> predList) {
		final Map<ParEqualsPredicate, SuperType> preds = new HashMap<>();
		for (final Constraint cons : predList) {
			final Pred pred = (Pred) cons;
			final List<ICryptSLPredicateParameter> variables = new ArrayList<>();

			if (pred.getParList() != null) {
				boolean firstPar = true;
				for (final SuPar var : pred.getParList().getParameters()) {
					if (var.getVal() != null) {
						ObjectImpl object = (ObjectImpl) ((LiteralExpression) var.getVal().getLit().getName())
								.getValue();
						ObjectDecl objectDecl = (ObjectDecl) object.eContainer();
						String type = objectDecl.getObjectType().getQualifiedName()
								+ ((objectDecl.getArray() != null) ? objectDecl.getArray() : "");
						;
						String name = object.getName();
						if (name == null) {
							name = "this";
							type = curClass;
						}
						variables.add(new CryptSLObject(name, type));
					} else {
						if (firstPar) {
							variables.add(new CryptSLObject("this", curClass));
						} else {
							variables.add(new CryptSLObject("_", "null"));
						}
					}
					firstPar = false;
				}
			}
			final String meth = pred.getPredName();
			final SuperType cond = pred.getLabelCond();
			if (cond == null) {
				preds.put(new ParEqualsPredicate(null, meth, variables, false), null);
			} else {
				preds.put(new ParEqualsPredicate(null, meth, variables, false), cond);
			}

		}
		return preds;
	}

	private Set<StateNode> getStatesForMethods(final List<CryptSLMethod> condMethods) {
		final Set<StateNode> predGens = new HashSet<>();
		if (condMethods.size() != 0) {
			for (final TransitionEdge methTrans : this.smg.getAllTransitions()) {
				final List<CryptSLMethod> transLabel = methTrans.getLabel();
				if (transLabel.size() > 0 && (transLabel.equals(condMethods)
						|| (condMethods.size() == 1 && transLabel.contains(condMethods.get(0))))) {
					predGens.add(methTrans.getRight());
				}
			}
		}
		return predGens;
	}

	private String getValueOfLiteral(final EObject name) {
		String value = "";
		if (name instanceof LiteralExpression) {
			final SuperType preValue = ((LiteralExpression) name).getValue();
			if (preValue != null) {
				value = preValue.getName();
			} else {
				EObject cons = ((LiteralExpression) name).getCons();
				if (cons instanceof LiteralExpression) {
					value = getValueOfLiteral(((LiteralExpression) cons).getName());
				} else {
					value = "";
				}
			}
		} else {
			value = ((Literal) name).getVal();
		}
		return Utils.filterQuotes(value);
	}

	private void storeRuletoFile(final CryptSLRule rule, final String folderPath, final String className) {
		FileOutputStream fileOut;
		try {
			fileOut = new FileOutputStream(folderPath + Constants.innerFileSeparator + className + ".cryptslbin");
			final ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(rule);
			out.close();
			fileOut.close();
			final FileInputStream fileIn = new FileInputStream(
					folderPath + Constants.innerFileSeparator + className + ".cryptslbin");
			final ObjectInputStream in = new ObjectInputStream(fileIn);
			in.readObject();
			in.close();
			fileIn.close();

		} catch (IOException | ClassNotFoundException e) {
			Activator.getDefault().logError(e);
		}
	}

	// private void loadModelFromFile(String outputURI) {
	// ResourceSet resSet = new ResourceSetImpl();
	// Resource xmiResourceRead = resSet.getResource(URI.createURI(outputURI),
	// true);
	// xmiResourceRead.getContents().get(0);
	//// Domainmodel dmro =
	// }

	// private String storeModelToFile(XtextResourceSet resourceSet, EObject
	// eObject, String className) throws IOException {
	// //Store the model to path outputURI
	// String outputURI = "file:///C:/Users/stefank3/Desktop/" + className + ".xmi";
	// Resource xmiResource = resourceSet.createResource(URI.createURI(outputURI));
	// xmiResource.getContents().add(eObject);
	// xmiResource.save(null);
	// return outputURI;
	// }

}
