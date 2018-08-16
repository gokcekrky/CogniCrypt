package de.cognicrypt.codegenerator.generator;

import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import crypto.interfaces.ISLConstraint;
import crypto.rules.CryptSLComparisonConstraint;
import crypto.rules.CryptSLConstraint;
import crypto.rules.CryptSLConstraint.LogOps;
import crypto.rules.CryptSLMethod;
import crypto.rules.CryptSLObject;
import crypto.rules.CryptSLPredicate;
import crypto.rules.CryptSLRule;
import crypto.rules.CryptSLSplitter;
import crypto.rules.CryptSLValueConstraint;
import crypto.rules.StateMachineGraph;
import crypto.rules.TransitionEdge;
import de.cognicrypt.codegenerator.Activator;
import de.cognicrypt.codegenerator.wizard.Configuration;
import de.cognicrypt.codegenerator.wizard.CrySLConfiguration;
import de.cognicrypt.core.Constants;
import de.cognicrypt.utils.Utils;

/**
 * 
 * @author Florian Breitfelder
 * @author Stefan Krueger
 *
 */
public class CrySLBasedCodeGenerator extends CodeGenerator {

	private List<List<CryptSLRule>> rules;
	/**
	 * Hash table to store the values that are assigend to variables.
	 */
	Hashtable<String, String> parameterValues = new Hashtable<String, String>();

	/**
	 * Contains the exceptions classes that are thrown by the generated code.
	 */
	private List<String> exceptions = new ArrayList<String>();

	private Map<String, CryptSLObject> methToReturnValue = new HashMap<String, CryptSLObject>();

	/**
	 * This constructor allows it to set a specific class and method names that are used in the generated Java code.
	 * 
	 * @param cryptslRule
	 *        Name of the cryptsl rule that should by transformed into java code.
	 * @param className
	 *        Class name that is used for the generated Java class.
	 * @param methodName
	 *        Method name that is usd for the generated Java code
	 * @throws Exception
	 */

	public CrySLBasedCodeGenerator(IProject targetProject) throws Exception {
		super(targetProject);

	}

	@Override
	public boolean generateCodeTemplates(Configuration chosenConfig, String pathToFolderWithAdditionalResources) {
		if (chosenConfig instanceof CrySLConfiguration) {
			this.rules = ((CrySLConfiguration) chosenConfig).getRules();
		}

		boolean next = true;
		exceptions.add("GeneralSecurityException");
		String genFolder = "";
		try {
			genFolder = this.project.getProjectPath() + Constants.innerFileSeparator + this.project
				.getSourcePath() + Constants.CodeGenerationCallFolder + Constants.innerFileSeparator;
		} catch (CoreException e1) {
			Activator.getDefault().logError(e1);
		}
		List<GeneratorClass> generatedClasses = new ArrayList<GeneratorClass>();
		Map<String, List<CryptSLPredicate>> reliablePreds = new HashMap<String, List<CryptSLPredicate>>();
		Map<String, List<String>> tmpUsagePars = new HashMap<String, List<String>>();

		GeneratorClass templateClass = new GeneratorClass();
		templateClass.setPackageName(Constants.PackageName);

		for (String imp : Constants.xmlimportsarr) {
			templateClass.addImport(imp);
		}
		templateClass.setClassName("Output");
		templateClass.setModifier("public");
		GeneratorMethod tmplUsage = new GeneratorMethod();
		templateClass.addMethod(tmplUsage);
		tmplUsage.setModifier("public");
		tmplUsage.setReturnType("void");
		tmplUsage.setName(Constants.NameOfTemporaryMethod);

		RuleDependencyTree rdt = new RuleDependencyTree(Utils.readCrySLRules());
		Map<CryptSLPredicate, Entry<CryptSLRule, CryptSLRule>> predicateConnections = new HashMap<CryptSLPredicate, Entry<CryptSLRule, CryptSLRule>>();
		List<CryptSLRule> mashedRuleList = rules.stream().reduce((a, b) -> {
			List<CryptSLRule> c = new ArrayList<CryptSLRule>(a);
			c.addAll(b);
			return c;
		}).get();
		for (int i = 0; i < mashedRuleList.size(); i++) {
			// Determine possible valid parameter values be analysing
			// the given constraints
			// ################################################################
			analyseConstraints(mashedRuleList.get(i).getConstraints());

			if (i < mashedRuleList.size() - 1) {
				CryptSLRule nextRule = mashedRuleList.get(i + 1);
				CryptSLRule curRule = mashedRuleList.get(i);

				if (rdt.hasDirectPath(curRule, nextRule)) {
					for (CryptSLPredicate ensPred : curRule.getPredicates()) {
						for (CryptSLPredicate reqPred : nextRule.getRequiredPredicates()) {

							if (reqPred.equals(ensPred) && isSubType(((CryptSLObject) reqPred.getParameters().get(0)).getJavaType(),
								((CryptSLObject) ensPred.getParameters().get(0)).getJavaType())) {
								if (predicateConnections.get(ensPred) != null) {
									int newParNumber = getParameterNumber(curRule, (CryptSLObject) ensPred.getParameters().get(0));
									int oldParNumber = getParameterNumber(curRule,
										(CryptSLObject) predicateConnections.keySet().stream().filter(e -> e.equals(ensPred)).findFirst().get().getParameters().get(0));
									if (newParNumber < oldParNumber) {
										predicateConnections.remove(ensPred);
										predicateConnections.put(ensPred, new SimpleEntry<CryptSLRule, CryptSLRule>(curRule, nextRule));
									}
								} else {
									predicateConnections.put(ensPred, new SimpleEntry<CryptSLRule, CryptSLRule>(curRule, nextRule));
								}
							}
						}
					}
				}
			}
		}
		for (List<CryptSLRule> ruleList : rules) {
			String usedClass = ruleList.get(ruleList.size() - 1).getClassName();
			String simpleClassName = usedClass.substring(usedClass.lastIndexOf('.') + 1);
			String newClass = "CogniCrypt" + simpleClassName;
			// Create code object that includes the generated java code
			GeneratorClass ruleClass = new GeneratorClass();
			ruleClass.setClassName(newClass);

			// generate Java code
			// ################################################################

			ruleClass.setPackageName(Constants.PackageName);

			// class definition
			ruleClass.setModifier("public");

			// method definition
			// ################################################################

			GeneratorMethod useMethod = new GeneratorMethod();
			useMethod.setName("use" + newClass);
			useMethod.setModifier("public");
			tmpUsagePars.put(useMethod.getName(), new ArrayList<String>());

			for (CryptSLRule rule : ruleList) {

				boolean lastRule = ruleList.get(ruleList.size() - 1).equals(rule);
				// get state machine of cryptsl rule
				StateMachineGraph stateMachine = rule.getUsagePattern();

				// analyse state machine
				StateMachineGraphAnalyser stateMachineGraphAnalyser = new StateMachineGraphAnalyser(stateMachine);
				ArrayList<List<TransitionEdge>> transitionsList;
				Iterator<List<TransitionEdge>> transitions = null;
				try {
					transitionsList = stateMachineGraphAnalyser.getTransitions();
					transitionsList.sort(new Comparator<List<TransitionEdge>>() {

						@Override
						public int compare(List<TransitionEdge> element1, List<TransitionEdge> element2) {
							return Integer.compare(element1.size(), element2.size());
						}
					});
					transitions = transitionsList.iterator();
				} catch (Exception e) {
					Activator.getDefault().logError(e);
				}

				do {
					// Load one possible path through the state machine.
					List<TransitionEdge> currentTransitions = transitions.next();
					ArrayList<Entry<String, String>> methodParametersOfSuperMethod = new ArrayList<Entry<String, String>>();

					String returnType = getReturnType(currentTransitions, usedClass);
					useMethod.setReturnType(returnType);

					// Determine imports, method calls and thrown exceptions
					ArrayList<String> imports = new ArrayList<String>(determineImports(currentTransitions));
					imports.addAll(Arrays.asList(Constants.xmlimportsarr));
					ruleClass.addImports(imports);

					Map<CryptSLPredicate, Entry<CryptSLRule, CryptSLRule>> usablePreds = new HashMap<>();
					for (Entry<CryptSLPredicate, Entry<CryptSLRule, CryptSLRule>> entry : predicateConnections.entrySet()) {
						if (entry.getValue().getValue().getClassName().equals(usedClass)) {
							usablePreds.put(entry.getKey(), entry.getValue());
						}
					}

					Optional<Entry<CryptSLPredicate, Entry<CryptSLRule, CryptSLRule>>> toBeEnsured = predicateConnections.entrySet().stream()
						.filter(e -> rule.getPredicates().contains(e.getKey())).findFirst();
					ArrayList<String> methodInvocations = generateMethodInvocations(rule.getClassName().substring(rule.getClassName().lastIndexOf('.') + 1), currentTransitions,
						methodParametersOfSuperMethod, usablePreds, toBeEnsured.isPresent() ? toBeEnsured.get() : null, imports, lastRule);
					if (methodInvocations.isEmpty()) {
						continue;
					}

					useMethod.addStatementToBody("System.out.println(\"Method is running :-)\");");
					for (String methodInvocation : methodInvocations) {
						useMethod.addStatementToBody(methodInvocation);
					}

					// add thrown exceptions
					useMethod.addExceptions(exceptions);
					
					Iterator<Entry<String, String>> iMethodParameters = methodParametersOfSuperMethod.iterator();
					do {
						if (iMethodParameters.hasNext()) {
							Entry<String, String> parameter = iMethodParameters.next();
							useMethod.addParameter(parameter);
							tmpUsagePars.get(useMethod.getName()).add(parameter.getValue() + " " + parameter.getKey());
						}

					} while (iMethodParameters.hasNext());

					reliablePreds.put(rule.getClassName(), rule.getPredicates());
					next = false;

				} while (next);
			}
			ruleClass.addMethod(useMethod);
			generatedClasses.add(ruleClass);
		}

		List<String> allParameters = tmpUsagePars.values().stream().flatMap(List::stream).map(String::new).collect(Collectors.toList());
		tmplUsage.addException("GeneralSecurityException");

		List<String> matched = new ArrayList<>();
		for (int j = 0; j < generatedClasses.size(); j++) {
			GeneratorClass generatedClass = generatedClasses.get(j);
			String className = generatedClass.getClassName();
			tmplUsage.addStatementToBody(className + " " + className.toLowerCase() + " = new " + className + "();");

			GeneratorMethod useMethod = generatedClass.getUseMethod();
			String methodName = useMethod.getName();

			if (methToReturnValue.keySet().contains(methodName) && !useMethod.getReturnType().equals("void") && j < generatedClasses.size() - 1) {

				//Two rounds: 
				//1. Match parameter type and name
				//2. If no match, match parameter type

				GeneratorMethod nextUseMethod = generatedClasses.get(j + 1).getUseMethod();
				String returnType = useMethod.getReturnType();
				int previous = matched.size();
				for (Entry<String, String> nextPar : nextUseMethod.getParameters()) {
					for (int i = 0; i < allParameters.size(); i++) {
						String curParTmpUsage = allParameters.get(i);
						String[] parPair = curParTmpUsage.split(" ");
						String parType = parPair[0];
						String parName = parPair[1];

						if (methToReturnValue.entrySet().stream()
							.anyMatch(e -> e.getKey().equals(
								methodName) && (isSubType(parType, e.getValue().getJavaType()) || isSubType(e.getValue().getJavaType(), parType))) && (isSubType(nextPar.getValue(),
									parType) || isSubType(parType, nextPar.getValue())) && (isSubType(parType, returnType) || isSubType(returnType, parType)) && nextPar.getKey()
										.equals(parName)) {
							tmplUsage.addStatementToBody(curParTmpUsage + " = ");
							matched.add(curParTmpUsage);
							break;
						}
					}

				}

				if (previous == matched.size()) {
					for (Entry<String, String> nextPar : nextUseMethod.getParameters()) {
						for (int i = 0; i < allParameters.size(); i++) {
							String curParTmpUsage = allParameters.get(i);
							String[] parPair = curParTmpUsage.split(" ");
							String parType = parPair[0];
							if (methToReturnValue.entrySet().stream()
								.anyMatch(e -> e.getKey().equals(methodName) && (isSubType(parType, e.getValue().getJavaType()))) && (isSubType(nextPar.getValue(),
									parType) || isSubType(parType, nextPar.getValue())) && (isSubType(parType, returnType) || isSubType(returnType, parType))) {
								tmplUsage.addStatementToBody(curParTmpUsage + " = ");
								matched.add(curParTmpUsage);
								break;
							}
						}
					}
				}

			}

			String useMethodReturnType = generatedClass.getMethods().get(0).getReturnType();
			if (j == generatedClasses.size() - 1 && !useMethodReturnType.equals("void")) {
				tmplUsage.addStatementToBody("return ");
				tmplUsage.setReturnType(useMethodReturnType);
			}
			tmplUsage.addStatementToBody(className.toLowerCase() + "." + methodName + "(");

			List<String> parList = tmpUsagePars.get(methodName);
			for (int i = 0; i < parList.size(); i++) {
				tmplUsage.addStatementToBody(parList.get(i).split(" ")[1] + (i < parList.size() - 1 ? "," : ""));
			}
			tmplUsage.addStatementToBody(");\n");

			allParameters.removeAll(matched);
			for (String par : allParameters) {
				tmplUsage.addParameter(new SimpleEntry<String, String>(par.split(" ")[1], par.split(" ")[0]));
			}

		}
		generatedClasses.add(templateClass);
		CodeHandler codeHandler = new CodeHandler(generatedClasses);
		final IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		try {
			codeHandler.writeToDisk(genFolder);
			cleanUpProject(page.getActiveEditor());
			final IFile outputFile = this.project.getIFile(templateClass.getAssociatedJavaFile().getAbsolutePath());
			IDE.openEditor(page, outputFile, PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor().getSite().getId());
		} catch (Exception e) {
			Activator.getDefault().logError(e);
		}

		return generatedClasses != null;
	}

	private int getParameterNumber(CryptSLRule curRule, CryptSLObject par) {
		Set<TransitionEdge> transitions = new HashSet<TransitionEdge>(curRule.getUsagePattern().getAllTransitions());
		for (TransitionEdge trans : transitions) {
			for (CryptSLMethod potMethod : trans.getLabel()) {
				SimpleEntry<String, String> cmpPar = new SimpleEntry<String, String>(par.getVarName(), par.getJavaType());
				if (potMethod.getParameters().contains(cmpPar)) {
					return potMethod.getParameters().size() - 1;
				} else if (potMethod.getRetObject().equals(cmpPar)) {
					return potMethod.getParameters().size();
				}

			}
		}
		return 0;
	}

	private boolean isSubType(String cmp1, String cmp2) {
		boolean subTypes = cmp1.equals(cmp2);
		if (!subTypes) {
			try {
				subTypes = Class.forName(cmp1).isAssignableFrom(Class.forName(cmp2));
			} catch (ClassNotFoundException e) {}
		}
		return subTypes;
	}

	/**
	 * This method generates a method invocation for every transition of a state machine that represents a cryptsl rule.
	 * 
	 * @param currentTransitions
	 *        List of transitions that represents a cryptsl rule's state machine.
	 * @param methodParametersOfSuperMethod
	 * @param predicateConnections
	 * @param imports
	 */
	private ArrayList<String> generateMethodInvocations(String className, List<TransitionEdge> currentTransitions, ArrayList<Entry<String, String>> methodParametersOfSuperMethod, Map<CryptSLPredicate, Entry<CryptSLRule, CryptSLRule>> usablePreds, Entry<CryptSLPredicate, Entry<CryptSLRule, CryptSLRule>> toBeEnsuredPred, List<String> imports, boolean lastRule) {
		ArrayList<String> methodInvocations = new ArrayList<String>();
		boolean ensures = false;
		for (TransitionEdge transition : currentTransitions) {
			CryptSLMethod method = null;

			for (CryptSLMethod meth : transition.getLabel()) {
				if (method != null) {
					break;
				}

				if (toBeEnsuredPred != null) {
					//Predicate
					method = fetchCorrespondingMethod(toBeEnsuredPred, meth);
					if (method != null) {
						ensures = true;
					}
				}

				for (Entry<CryptSLPredicate, Entry<CryptSLRule, CryptSLRule>> usablePred : usablePreds.entrySet()) {
					if (method == null) {
						method = fetchCorrespondingMethod(usablePred, meth);
					} else {
						break;
					}
				}

			}
			// Determine method name and signature
			if (method == null) {
				method = transition.getLabel().get(0);
			}
			String methodName = method.getMethodName();
			methodName = methodName.substring(methodName.lastIndexOf(".") + 1);

			// Determine parameter of method.
			List<Entry<String, String>> parameters = method.getParameters();
			Iterator<Entry<String, String>> parametersIterator = parameters.iterator();

			StringBuilder sourceLineGenerator = new StringBuilder(methodName);
			sourceLineGenerator.append("(");

			do {
				if (parametersIterator.hasNext()) {
					sourceLineGenerator.append(parametersIterator.next().getKey());
				}

				if (parametersIterator.hasNext()) {
					sourceLineGenerator.append(", ");
				}

			} while (parametersIterator.hasNext());

			sourceLineGenerator.append(");");

			Class<?>[] methodParameter = collectParameterTypes(parameters);

			try {
				determineThrownExceptions(method.getMethodName().substring(0, method.getMethodName().lastIndexOf(".")), methodName, methodParameter, imports);
			} catch (NoSuchMethodException | SecurityException | ClassNotFoundException e) {
				Activator.getDefault().logError(e);
			}

			// TODO determine possible subclasses
			// ################################################################
			// see also method getSubClass(className);
			String lastInvokedMethod = getLastInvokedMethodName(currentTransitions).toString();
			String methodInvocation = generateMethodInvocation(lastInvokedMethod, methodParametersOfSuperMethod, imports, method, methodName, parameters, className,
				sourceLineGenerator, lastRule);

			// Add new generated method invocation
			if (!methodInvocation.equals("")) {
				methodInvocations.add(methodInvocation);
				methodInvocation = "";
			}

		}
		if (toBeEnsuredPred == null || ensures) {
			return methodInvocations;
		} else {
			return new ArrayList<String>();
		}
	}

	private CryptSLMethod fetchCorrespondingMethod(Entry<CryptSLPredicate, Entry<CryptSLRule, CryptSLRule>> pred, CryptSLMethod meth) {
		CryptSLObject objectOfPred = (CryptSLObject) pred.getKey().getParameters().get(0);
		String predVarType = objectOfPred.getJavaType();
		String predVarName = objectOfPred.getVarName();

		//Method
		Entry<String, String> retObject = meth.getRetObject();
		String returnType = retObject.getValue();
		String returnVarName = retObject.getKey();
		String className = pred.getValue().getKey().getClassName();
		String classSimpleName = className.substring(className.lastIndexOf('.') + 1);

		if (isSubType(predVarType, returnType) && returnVarName.equals(predVarName)) {
			methToReturnValue.put("useCogniCrypt" + classSimpleName, objectOfPred); // new CryptSLObject(predVarName, returnType));
			return meth;

		} else if (predVarName.equals("this") && meth.getMethodName().endsWith(predVarType.substring(predVarType.lastIndexOf('.') + 1))) {
			methToReturnValue.put("useCogniCrypt" + classSimpleName, objectOfPred); //new CryptSLObject(returnClassName.toLowerCase(), returnClassName));
			return meth;
		} else {
			for (Entry<String, String> par : meth.getParameters()) {
				String parType = par.getValue();
				String parVarName = par.getKey();

				if ((isSubType(predVarType, parType) || isSubType(parType, predVarType)) && parVarName.equals(predVarName)) {
					methToReturnValue.put("useCogniCrypt" + classSimpleName, objectOfPred);
					return meth;
				}
			}
		}
		return null;
	}

	private Class<?>[] collectParameterTypes(List<Entry<String, String>> parameters) {
		Class<?>[] methodParameter = new Class<?>[parameters.size()];
		int i = 0;
		List<String> primitiveTypes = Arrays.asList(new String[] { "int", "boolean", "short", "double", "float", "long", "byte", "int[]", "byte[]", "char[]" });

		for (Entry<String, String> parameter : parameters) {
			if (primitiveTypes.contains(parameter.getValue())) {
				Class<?> primitiveType = null;
				switch (parameter.getValue()) {
					case "int":
						primitiveType = int.class;
						break;
					case "double":
						primitiveType = double.class;
						break;
					case "boolean":
						primitiveType = boolean.class;
						break;
					case "float":
						primitiveType = float.class;
						break;
					case "byte":
						primitiveType = byte.class;
						break;
					case "byte[]":
						primitiveType = byte[].class;
						break;
					case "int[]":
						primitiveType = int[].class;
						break;
					case "char[]":
						primitiveType = char[].class;
						break;
					default:
						primitiveType = int.class;
				}
				methodParameter[i] = primitiveType;

			} else {
				try {
					methodParameter[i] = Class.forName(parameter.getValue());
					i++;
				} catch (ClassNotFoundException e) {
					System.out.println("No class found for type: " + parameter.getValue().toString());
					e.printStackTrace();
				}
			}
		}
		return methodParameter;
	}

	private String generateMethodInvocation(String lastInvokedMethod, ArrayList<Entry<String, String>> methodParametersOfSuperMethod, List<String> imports, CryptSLMethod method, String methodName, List<Entry<String, String>> parameters, String className, StringBuilder currentInvokedMethod, boolean lastRule) {
		// Generate method invocation. Hereafter, a method call is distinguished in three categories.
		String methodInvocation = "";

		String instanceName = className.substring(0, 1).toLowerCase() + className.substring(1);

		// 1. Constructor method calls
		// 2. Static method calls
		// 3. Instance method calls

		// 1. Constructor method call
		if (currentInvokedMethod.substring(0, currentInvokedMethod.indexOf("(")).equals(className)) {

			methodInvocation = className + " " + instanceName + " = new " + currentInvokedMethod;

			if (methodName.equals(lastInvokedMethod)) {
				methodInvocation = methodInvocation + "\nreturn " + instanceName + ";";
			}
		}
		// Static method call
		else if (currentInvokedMethod.toString().contains("getInstance")) {
			currentInvokedMethod = new StringBuilder(currentInvokedMethod.substring(currentInvokedMethod.lastIndexOf("=") + 1).trim());
			methodInvocation = className + " " + instanceName + " = " + className + "." + currentInvokedMethod;

			if (methodName.equals(lastInvokedMethod.substring(lastInvokedMethod.lastIndexOf('.') + 1))) {
				methodInvocation = methodInvocation + "\nreturn " + instanceName + ";";
			}
		}
		// 3. Instance method call
		else {
			// Does method have a return value?
			if (method.getRetObject() != null) {
				String returnValueType = method.getRetObject().getValue();
				boolean generated = false;
				String voidString = "void";

				// Determine lastInvokedMethod
				lastInvokedMethod = lastInvokedMethod.substring(lastInvokedMethod.lastIndexOf('.') + 1);

				if (lastRule) {
					// Last invoked method and return type is not equal to "void".
					if (methodName.equals(lastInvokedMethod) && !returnValueType.equals(voidString)) {
						methodInvocation = "return " + instanceName + "." + currentInvokedMethod;
						generated = true;
					}
					// Last invoked method and return type is equal to "void".
					else if (methodName.equals(lastInvokedMethod) && returnValueType.equals(voidString)) {
						methodInvocation = instanceName + "." + currentInvokedMethod + "\nreturn " + instanceName + ";";
						generated = true;
					}
					// Not the last invoked method and return type is not equal to "void".
					else if (!methodName.equals(lastInvokedMethod) && !returnValueType.equals(voidString)) {
						methodInvocation = returnValueType + " = " + instanceName + "." + currentInvokedMethod;
						generated = true;
					}
				}
				if (!generated) {
					methodInvocation = instanceName + "." + currentInvokedMethod;
				}
			} else {
				methodInvocation = instanceName + "." + currentInvokedMethod;
			}
		}

		// Replace parameters by values that are defined in the previous step
		// ################################################################
		methodInvocation = replaceParameterByValue(parameters, methodInvocation, methodParametersOfSuperMethod, imports);
		return methodInvocation;
	}

	/**
	 * This method analyses ISLConstraints to determine possible valid values for variables.
	 * 
	 * @param constraints
	 *        List of constraints that are used for the analysis.
	 */
	private void analyseConstraints(List<ISLConstraint> constraints) {
		for (ISLConstraint constraint : constraints) {
			// handle CryptSLValueConstraint
			if (constraint instanceof CryptSLValueConstraint) {
				CryptSLValueConstraint cryptSLValueConstraint = (CryptSLValueConstraint) constraint;
				resolveCryptSLValueConstraint(cryptSLValueConstraint);
			}
			// handle CryptSLConstraint
			else if (constraint instanceof CryptSLConstraint) {
				CryptSLConstraint cryptSLConstraint = (CryptSLConstraint) constraint;

				// (CryptSLConstrant | CryptSLValueConstraint => CryptSLConstrant | CryptSLValueConstraint)
				if ((cryptSLConstraint.getLeft() instanceof CryptSLConstraint || cryptSLConstraint.getRight() instanceof CryptSLValueConstraint) && cryptSLConstraint
					.getOperator() == LogOps.implies && (cryptSLConstraint
						.getRight() instanceof CryptSLConstraint || cryptSLConstraint.getRight() instanceof CryptSLValueConstraint)) {

					// 1. step verify premise
					if (resolveCryptSLConstraint(cryptSLConstraint.getLeft())) {
						// 2. step verify conclusion
						resolveCryptSLConstraint(cryptSLConstraint.getRight());
					}
				}
			} else if (constraint instanceof CryptSLComparisonConstraint) {
				CryptSLComparisonConstraint comp = (CryptSLComparisonConstraint) constraint;
				if (comp.getLeft().getLeft() instanceof CryptSLObject && comp.getRight().getLeft() instanceof CryptSLObject) {
					CryptSLObject left = (CryptSLObject) comp.getLeft().getLeft();
					CryptSLObject right = (CryptSLObject) comp.getRight().getLeft();
					int value;
					String varName = "";
					try {
						value = Integer.parseInt(left.getName());
						varName = right.getVarName();
					} catch (NumberFormatException ex) {
						value = Integer.parseInt(right.getName());
						varName = left.getVarName();
					}

					switch (comp.getOperator()) {
						case g:
						case ge:
							try {
								parameterValues.put(varName, String.valueOf(SecureRandom.getInstance("SHA1PRNG").nextInt(2*value) + value));
							} catch (NoSuchAlgorithmException e1) {}
							break;
						case l:
						case le:
							try {
								parameterValues.put(varName, String.valueOf(SecureRandom.getInstance("SHA1PRNG").nextInt(value)));
							} catch (NoSuchAlgorithmException e) {}
							break;
						case neq:
							try {
								parameterValues.put(varName, String.valueOf(SecureRandom.getInstance("SHA1PRNG").nextInt(value - 1)));
							} catch (NoSuchAlgorithmException e) {}
							break;
						case eq:
						default:
							break;
					}
				}
			}
		}
	}

	/**
	 * Replaces parameter names in method invocations by a value. This value is derived by constraints.
	 * 
	 * @param parameters
	 *        All available parameters.
	 * @param constraints
	 *        Available constraints for parameters
	 * 
	 * @param currentInvokedMethod
	 *        Method invocation as string
	 * @param methodParametersOfSuperMethod
	 * @param imports
	 * 
	 * @return New method invocation as string (parameter names are replaces by values)
	 */
	private String replaceParameterByValue(List<Entry<String, String>> parameters, String currentInvokedMethod, ArrayList<Entry<String, String>> methodParametersOfSuperMethod, List<String> imports) {

		// Split current method invocation "variable = method(method parameter)" in:
		// 1. variable = method
		// 2. (method parameter)
		// replace only parameter names by values in the second part.
		String methodNamdResultAssignment = currentInvokedMethod.substring(0, currentInvokedMethod.indexOf("("));
		String methodParameter = currentInvokedMethod.substring(currentInvokedMethod.indexOf("("), currentInvokedMethod.indexOf(")"));
		String appendix = currentInvokedMethod.substring(currentInvokedMethod.indexOf(")"), currentInvokedMethod.length());

		for (Entry<String, String> parameter : parameters) {

			if (parameterValues.containsKey(parameter.getKey())) {
				String value = parameterValues.get(parameter.getKey());
				// replace parameter by value
				if (parameter.getValue().equals("java.lang.String")) {
					methodParameter = methodParameter.replace(parameter.getKey(), "\"" + value + "\"");
				} else {
					methodParameter = methodParameter.replace(parameter.getKey(), value);
				}
			} else if (currentInvokedMethod.contains("Cipher.getInstance")) {
				String firstParameter = parameter.getKey() + "[0]";
				String secondParameter = parameter.getKey() + "[1]";
				String thirdParameter = parameter.getKey() + "[2]";
				String value = "\"";

				if (parameterValues.containsKey(firstParameter) && !parameterValues.get(firstParameter).equals("")) {
					value = value + parameterValues.get(firstParameter);

					if (parameterValues.containsKey(secondParameter) && !parameterValues.get(secondParameter).equals("")) {
						value = value + "/" + parameterValues.get(secondParameter);

						if (parameterValues.containsKey(thirdParameter) && !parameterValues.get(thirdParameter).equals("")) {
							value = value + "/" + parameterValues.get(thirdParameter);
						}
					}

					value = value + "\"";
					methodParameter = methodParameter.replace(parameter.getKey(), value);
				}
			} else {
				// If no value can be assigned add variable to the parameter list of the super method
				// Check type name for "."
				if (parameter.getValue().contains(".")) {
					methodParametersOfSuperMethod.add(new SimpleEntry<String, String>(parameter.getKey(), parameter.getValue()));
					imports.add(parameter.getValue());
				} else {
					methodParametersOfSuperMethod.add(parameter);
				}

			}
		}

		currentInvokedMethod = methodNamdResultAssignment + methodParameter + appendix;
		return currentInvokedMethod;
	}

	/**
	 * This method assigns a value to a variable by analysing a CryptSLValueConstraint object.
	 * 
	 * If the assigned value is valid this method returns true otherwise false
	 * 
	 * @param cryptSLValueConstraint
	 *        CryptSLValueConstraint object that is used to determine a value.
	 * 
	 * @return If the assigned value is valid this method returns true otherwise false
	 */
	private boolean resolveCryptSLValueConstraint(CryptSLValueConstraint cryptSLValueConstraint) {
		CryptSLObject cryptSLObject = cryptSLValueConstraint.getVar();
		CryptSLSplitter cryptSLSplitter = cryptSLObject.getSplitter();

		String parameterNameKey = "";

		// Distinguish between regular variable assignments and
		// part assignments.
		if (cryptSLSplitter == null) {
			parameterNameKey = cryptSLValueConstraint.getVarName();
		} else {
			parameterNameKey = cryptSLObject.getVarName() + "[" + cryptSLSplitter.getIndex() + "]";
		}

		if (!parameterValues.containsKey(parameterNameKey)) {
			parameterValues.put(parameterNameKey, cryptSLValueConstraint.getValueRange().get(0));
		}

		if (cryptSLValueConstraint.getValueRange().contains(parameterValues.get(parameterNameKey))) {
			return true; // Assigned parameter value is in valid value range.
		} else {
			return false; // Assigned parameter value is not in valid value range.
		}
	}

	/**
	 * This method resolves constraints of a cryptsl rule recursively.
	 * 
	 * @param constraint
	 *        Constraint object that should be resolved.
	 * @return Returns true if the given constraint object describes a valid logical expression otherwise false.
	 */
	private boolean resolveCryptSLConstraint(ISLConstraint constraint) {
		if (constraint instanceof CryptSLValueConstraint) {

			CryptSLValueConstraint cryptSLValueConstraint = (CryptSLValueConstraint) constraint;
			return resolveCryptSLValueConstraint(cryptSLValueConstraint);

		} else if (constraint instanceof CryptSLConstraint) {

			CryptSLConstraint cryptSLConstraint = (CryptSLConstraint) constraint;
			LogOps operator = cryptSLConstraint.getOperator();

			if (operator == LogOps.and) {
				return resolveCryptSLConstraint(cryptSLConstraint.getLeft()) && resolveCryptSLConstraint(cryptSLConstraint.getRight());
			} else if (operator == LogOps.or) {
				return resolveCryptSLConstraint(cryptSLConstraint.getLeft()) || resolveCryptSLConstraint(cryptSLConstraint.getRight());
			} else if (operator == LogOps.implies) {
				if (resolveCryptSLConstraint(cryptSLConstraint.getLeft())) {
					return resolveCryptSLConstraint(cryptSLConstraint.getRight());
				} else {
					return true;
				}
			} else {
				return false; // invalid operator
			}
		}
		return false; // unsupported object type
	}

	/**
	 * Determine return type. The return type of the last invoked method is used for the return type of the generated method. If there is no method invoked that has a return type
	 * unequal to void the type of the used class is used.
	 * 
	 * @param transitions
	 *        All transitions that are used to describe the source code.
	 * @return Returns the return type as string.
	 */
	private String getReturnType(List<TransitionEdge> transitions, String className) {
		// Get last 
		CryptSLMethod lastInvokedMethod = getLastInvokedMethod(transitions);

		// Get return type
		String type = lastInvokedMethod.getRetObject().getValue();

		if (type.equals("void")) {
			return className;
		} else {
			return type;
		}
	}

	/**
	 * Returns the last invoked method of a CryptSLMethod object sequence.
	 * 
	 * @param transitions
	 *        Sequence
	 * @return Last invoked method.
	 */
	private CryptSLMethod getLastInvokedMethod(List<TransitionEdge> transitions) {
		// Get last transition
		TransitionEdge lastTransition = transitions.get(transitions.size() - 1);

		// Get last 
		CryptSLMethod lastInvokedMethod = lastTransition.getLabel().get(0);

		return lastInvokedMethod;
	}

	/**
	 * Returns the name of the last method that is used by the currently analysed cryptsl API-rule
	 * 
	 * @param transitions
	 *        All transitions of a state machine that describes a cryptsl API-rule
	 * 
	 * @return Name of the last method that is used by the currently analysed cryptsl API-rule.
	 */
	private String getLastInvokedMethodName(List<TransitionEdge> transitions) {
		String lastInvokedMethodName = getLastInvokedMethod(transitions).toString();
		lastInvokedMethodName = lastInvokedMethodName.substring(0, lastInvokedMethodName.lastIndexOf("("));

		if (lastInvokedMethodName.contains("=")) {
			lastInvokedMethodName = lastInvokedMethodName.substring(lastInvokedMethodName.lastIndexOf("=") + 1);
			lastInvokedMethodName = lastInvokedMethodName.trim();
		}
		return lastInvokedMethodName;
	}

	/**
	 * Adds the needed import instructions.
	 * 
	 * @param javaCodeFile
	 *        Java code object where the imports are added.
	 * 
	 * @param transitions
	 *        All transitions that are used to describe the source code.
	 */
	private Collection<String> determineImports(List<TransitionEdge> transitions) {
		Set<String> imports = new HashSet<String>();
		for (TransitionEdge transition : transitions) {
			String completeMethodName = transition.getLabel().get(0).getMethodName();
			imports.add(completeMethodName.substring(0, completeMethodName.lastIndexOf(".")));
		}
		return imports;
	}

	/**
	 * This method determines the exception classes that are thrown by a given method.
	 * 
	 * @param className
	 *        Class that contains the method that should by analysed.
	 * 
	 * @param methodName
	 *        Name of method that should by analysed.
	 * 
	 * @param methodParameters
	 *        Parameter of method to identify the method by their signature.
	 * @param imports
	 * 
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws ClassNotFoundException
	 */
	private void determineThrownExceptions(String className, String methodName, Class<?>[] methodParameters, List<String> imports) throws NoSuchMethodException, SecurityException, ClassNotFoundException {
		List<Class<?>> exceptionClasses = new ArrayList<Class<?>>();
		Method[] methods = java.lang.Class.forName(className).getMethods();
		for (Method meth : methods) {
			if (meth.getName().equals(methodName) && methodParameters.length == meth.getParameterCount()) {
				if (matchMethodParameters(methodParameters, meth.getParameterTypes())) {
					exceptionClasses.addAll(Arrays.asList(meth.getExceptionTypes()));
				}
			}
		}

		//.getMethod(methodName, methodParameters).getExceptionTypes();
		for (Class<?> exception : exceptionClasses) {

			imports.add(exception.getName());

			String exceptionClass = exception.getSimpleName();
			if (!exceptions.contains(exceptionClass)) {
				exceptions.add(exceptionClass);
			}

		}
	}

	private boolean matchMethodParameters(Class<?>[] methodParameters, Class<?>[] classes) {
		for (int i = 0; i < methodParameters.length; i++) {
			if (methodParameters[i].getName().equals("AnyType")) {
				continue;
			} else if (!methodParameters[i].equals(classes[i])) {
				return false;
			}
		}
		return true;
	}

}