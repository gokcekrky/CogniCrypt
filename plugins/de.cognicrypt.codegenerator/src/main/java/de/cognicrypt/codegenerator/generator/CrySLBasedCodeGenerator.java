package de.cognicrypt.codegenerator.generator;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import crypto.interfaces.ISLConstraint;
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
import de.cognicrypt.core.Constants;
import de.cognicrypt.utils.Utils;

/**
 * 
 * @author Florian Breitfelder
 * @author Stefan Krueger
 *
 */
public class CrySLBasedCodeGenerator extends CodeGenerator {

	public static Hashtable<String, CryptSLRule> rules = new Hashtable<String, CryptSLRule>();
	/**
	 * Hash table to store the values that are assigend to variables.
	 */
	Hashtable<String, String> parameterValues = new Hashtable<String, String>();

	/**
	 * Contains the exceptions classes that are thrown by the generated code.
	 */
	private ArrayList<String> exceptions = new ArrayList<String>();

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

	public CrySLBasedCodeGenerator(IProject targetProject, List<String> genRules) throws Exception {
		super(targetProject);
		for (String ruleName : genRules) {
			rules.put(ruleName, getCryptSLRule(ruleName));
		}
	}

	/**
	 * Returns the cryptsl rule with the name that is defined by the method parameter cryptslRule.
	 * 
	 * @param cryptslRule
	 *        Name of cryptsl rule that should by returend.
	 * 
	 * @return Returns the cryptsl rule with the name that is defined by the parameter cryptslRule.
	 * @throws Exception
	 *         Thows an exception if given rule name does not exist.
	 */
	public static CryptSLRule getCryptSLRule(String cryptslRule) throws Exception {
		final FileInputStream fileIn = new FileInputStream(Utils.getResourceFromWithin("resources/CrySLRules", de.cognicrypt.core.Activator.PLUGIN_ID)
			.getAbsolutePath() + "\\" + cryptslRule + ".cryptslbin");
		final ObjectInputStream in = new ObjectInputStream(fileIn);
		CryptSLRule rule = (CryptSLRule) in.readObject();
		in.close();
		fileIn.close();
		return rule;
	}

	@Override
	public boolean generateCodeTemplates(Configuration chosenConfig, String pathToFolderWithAdditionalResources) {
		boolean next = true;
		exceptions.add("GeneralSecurityException");
		String genFolder = "";
		try {
			genFolder = this.project.getProjectPath() + Constants.innerFileSeparator + this.project
				.getSourcePath() + Constants.CodeGenerationCallFolder + Constants.innerFileSeparator;
		} catch (CoreException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		List<File> codeFileList = null;
		Iterator<List<TransitionEdge>> transitions = null;
		Map<String, List<CryptSLPredicate>> reliablePreds = new HashMap<String, List<CryptSLPredicate>>();
		Map<String, List<String>> tmpUsagePars = new HashMap<String, List<String>>();

		JavaCodeFile tmpOutputFile = new JavaCodeFile(Constants.AdditionalOutputFile);
		tmpOutputFile.addCodeLine("package " + Constants.PackageName + ";");
		tmpOutputFile.addCodeLine("public class Output {");
		tmpOutputFile.addCodeLine("public void " + Constants.NameOfTemporaryMethod + "(");

		for (CryptSLRule rule : rules.values()) {
			String usedClass = rule.getClassName();
			String newClass = "CogniCrypt" + usedClass;
			// get state machine of cryptsl rule
			StateMachineGraph stateMachine = rule.getUsagePattern();

			List<CryptSLPredicate> usablePreds = new ArrayList<CryptSLPredicate>();
			if (!reliablePreds.isEmpty() && !rule.getRequiredPredicates().isEmpty()) {
				List<CryptSLPredicate> preds = rule.getRequiredPredicates();
				for (Entry<String, List<CryptSLPredicate>> l : reliablePreds.entrySet()) {
					preds.retainAll(l.getValue());
					usablePreds.addAll(l.getValue());
				}
			}

			// analyse state machine
			StateMachineGraphAnalyser stateMachineGraphAnalyser = new StateMachineGraphAnalyser(stateMachine);
			ArrayList<List<TransitionEdge>> transitionsList;
			try {
				transitionsList = stateMachineGraphAnalyser.getTransitions();
				transitionsList.sort(new Comparator<List<TransitionEdge>>() {
					// sort paths by number of nodes

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

				// Determine imports, method calls and thrown exceptions
				ArrayList<String> imports = new ArrayList<String>(determineImports(currentTransitions));
				imports.add("import java.security.GeneralSecurityException;");
				ArrayList<String> methodInvocations = generateMethodInvocations(rule, currentTransitions, methodParametersOfSuperMethod, usablePreds, imports);

				// Create code object that includes the generated java code
				JavaCodeFile javaCodeFile = new JavaCodeFile(newClass + ".java");

				// generate Java code
				// ################################################################

				javaCodeFile.addCodeLine("package " + Constants.PackageName + ";");

				// first add imports
				for (String ip : imports) {
					javaCodeFile.addCodeLine(ip);
				}

				// class definition
				javaCodeFile.addCodeLine("public class " + newClass + " {");

				// method definition
				// ################################################################

				String methodName = "use" + newClass;

				String returnType = getReturnType(currentTransitions, usedClass);

				String methodDefintion = "public " + returnType + " " + methodName + "(";

				Iterator<Entry<String, String>> iMethodParameters = methodParametersOfSuperMethod.iterator();
				tmpUsagePars.put(methodName, new ArrayList<String>());
				do {
					if (iMethodParameters.hasNext()) {
						Entry<String, String> parameter = iMethodParameters.next();
						String methParameter = parameter.getValue() + " " + parameter.getKey();
						methodDefintion = methodDefintion + methParameter;
						tmpUsagePars.get(methodName).add(methParameter);
					}

					// if a further parameters exist separate them by comma.
					if (iMethodParameters.hasNext()) {
						methodDefintion = methodDefintion + ", ";
					}

				} while (iMethodParameters.hasNext());

				methodDefintion = methodDefintion + ") ";

				// add thrown exceptions
				if (exceptions.size() > 0) {
					methodDefintion = methodDefintion + "throws ";

					Iterator<String> iExceptions = exceptions.iterator();

					do {
						String exception = iExceptions.next();
						methodDefintion = methodDefintion + exception;

						// if a further exception class follows separate them by comma
						if (iExceptions.hasNext()) {
							methodDefintion = methodDefintion + ", ";
						}

					} while (iExceptions.hasNext());

				}

				methodDefintion = methodDefintion + " {";

				javaCodeFile.addCodeLine(methodDefintion);

				// add method body

				// first method code line for test reasons
				javaCodeFile.addCodeLine("System.out.println(\"Method is running :-)\");");

				for (String methodInvocation : methodInvocations) {
					javaCodeFile.addCodeLine(methodInvocation);
				}

				// close method definition
				javaCodeFile.addCodeLine("}");
				// close class definition
				javaCodeFile.addCodeLine("}");

				// compile code
				// ################################################################
				File codeFile = null;
				try {
					codeFile = javaCodeFile.writeToDisk(genFolder);
				} catch (Exception e) {
					Activator.getDefault().logError(e);
				}
				if (codeFileList == null) {
					codeFileList = new ArrayList<File>();
				}
				codeFileList.add(codeFile);

				// Compiling is enabled for testing
				//codeHandler.compile();

				// execute code
				//next = !(codeHandler.run(newClass, "use", null, null));

				reliablePreds.put(rule.getClassName(), rule.getPredicates());

				next = false;

			} while (next);

		}

		List<String> allParameters = tmpUsagePars.values().stream().flatMap(List::stream).map(String::new).collect(Collectors.toList());
		for (int i = 0; i < allParameters.size(); i++) {
			tmpOutputFile.addCodeLine(allParameters.get(i) + (i < allParameters.size() - 1 ? "," : ""));
		}
		tmpOutputFile.addCodeLine(") throws GeneralSecurityException {");

		for (CryptSLRule rule : rules.values()) {
			String newClass = "CogniCrypt" + rule.getClassName();
			tmpOutputFile.addCodeLine(newClass + " " + newClass.toLowerCase() + " = new " + newClass + "();");
			String methodName = "use" + newClass;
			tmpOutputFile.addCodeLine(newClass.toLowerCase() + "." + methodName + "(");
			List<String> parList = tmpUsagePars.get(methodName);
			for (int i = 0; i < parList.size(); i++) {
				tmpOutputFile.addCodeLine(parList.get(i).split(" ")[1] + (i < tmpUsagePars.size() - 1 ? "," : ""));
			}
			tmpOutputFile.addCodeLine(");");
		}

		tmpOutputFile.addCodeLine("}");
		tmpOutputFile.addCodeLine("}");
		File writeToDisk = null;
		try {
			writeToDisk = tmpOutputFile.writeToDisk(genFolder);
			codeFileList.add(writeToDisk);
			CodeHandler codeHandler = new CodeHandler(codeFileList);
			final IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			final IFile outputFile = this.project.getIFile(this.project.getProjectPath() + Constants.innerFileSeparator + this.project
				.getSourcePath() + Constants.innerFileSeparator + Constants.PackageName + Constants.innerFileSeparator + writeToDisk.toPath().toString());
			final IEditorPart editor = IDE.openEditor(page, outputFile, PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor().getSite().getId());
			cleanUpProject(editor);
		} catch (Exception e) {
			Activator.getDefault().logError(e);
		}

		return codeFileList != null;
	}

	/**
	 * This method generates a method invocation for every transition of a state machine that represents a cryptsl rule.
	 * 
	 * @param currentTransitions
	 *        List of transitions that represents a cryptsl rule's state machine.
	 * @param methodParametersOfSuperMethod
	 * @param usablePreds
	 * @param imports
	 */
	private ArrayList<String> generateMethodInvocations(CryptSLRule rule, List<TransitionEdge> currentTransitions, ArrayList<Entry<String, String>> methodParametersOfSuperMethod, List<CryptSLPredicate> usablePreds, List<String> imports) {
		// Determine possible valid parameter values be analysing
		// the given constraints
		// ################################################################
		analyseConstraints(rule.getConstraints());

		ArrayList<String> methodInvocations = new ArrayList<String>();
		for (TransitionEdge transition : currentTransitions) {

			CryptSLMethod method = null;

			for (CryptSLMethod meth : transition.getLabel()) {
				if (method != null) {
					break;
				}
				for (CryptSLPredicate usablePred : usablePreds) {
					String predVarName = usablePred.getParameters().get(0).getName();
					for (Entry<String, String> o : meth.getParameters()) {
						if (predVarName.equals(o.getKey())) {
							method = meth;
							break;
						}
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
			String methodInvocation = generateMethodInvocation(lastInvokedMethod, methodParametersOfSuperMethod, imports, method, methodName, parameters, rule.getClassName(),
				sourceLineGenerator);

			// Add new generated method invocation
			if (!methodInvocation.equals("")) {
				methodInvocations.add(methodInvocation);
				methodInvocation = "";
			}

		}
		return methodInvocations;
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

	private String generateMethodInvocation(String lastInvokedMethod, ArrayList<Entry<String, String>> methodParametersOfSuperMethod, List<String> imports, CryptSLMethod method, String methodName, List<Entry<String, String>> parameters, String className, StringBuilder currentInvokedMethod) {
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

			if (methodName.equals(lastInvokedMethod)) {
				methodInvocation = methodInvocation + "\nreturn " + instanceName + ";";
			}
		}
		// 3. Instance method call
		else {
			// Does method have a return value?
			if (method.getRetObject() != null) {
				String returnValueType = method.getRetObject().getValue();

				// Determine lastInvokedMethod
				lastInvokedMethod = lastInvokedMethod.substring(lastInvokedMethod.lastIndexOf('.') + 1);

				// Last invoked method and return type is not equal to "void".
				String voidString = "void";
				if (methodName.equals(lastInvokedMethod) && !returnValueType.equals(voidString)) {
					methodInvocation = "return " + instanceName + "." + currentInvokedMethod;
				}
				// Last invoked method and return type is equal to "void".
				else if (methodName.equals(lastInvokedMethod) && returnValueType.equals(voidString)) {
					methodInvocation = instanceName + "." + currentInvokedMethod + "\nreturn " + instanceName + ";";
				}
				// Not the last invoked method and return type is not equal to "void".
				else if (!methodName.equals(lastInvokedMethod) && !returnValueType.equals(voidString)) {
					methodInvocation = returnValueType + " = " + instanceName + "." + currentInvokedMethod;
				}
				// Not the last invoked method and return type is equal to "void"
				else if (!methodName.equals(lastInvokedMethod) && returnValueType.equals(voidString)) {
					methodInvocation = instanceName + "." + currentInvokedMethod;
				} else {
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
					methodParametersOfSuperMethod
						.add(new SimpleEntry<String, String>(parameter.getKey(), parameter.getValue().substring(parameter.getValue().lastIndexOf(".") + 1)));
					imports.add("import " + parameter.getValue() + ";");
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
		String returnType = "void";
		// Get last 
		CryptSLMethod lastInvokedMethod = getLastInvokedMethod(transitions);

		// Get return type
		String type = lastInvokedMethod.getRetObject().getValue();

		if (type.equals("AnyType")) {
			returnType = className;
		} else {
			returnType = type;
		}

		return returnType;
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
			imports.add("import " + completeMethodName.substring(0, completeMethodName.lastIndexOf(".")) + ";");
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

			String exceptionImport = "import " + exception.getName() + ";";
			if (!exceptions.contains(exceptionImport)) {
				imports.add(exceptionImport);
			}

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

	/**
	 * 
	 * This method should determine subclasses to resolve sub types of interfaces. A possible approach is to use "reflections" (https://code.google.com/archive/p/reflections/) by
	 * adding
	 * 
	 * <dependency> <groupId>org.reflections</groupId> <artifactId>reflections</artifactId> <version>0.9.11</version> </dependency>
	 * 
	 * to the pom.xml
	 * 
	 * @param className
	 * @return
	 */
	private String getSubClass(String className) {

		//		Class<?> clazz;
		//		try {
		//			Reflections reflections = new Reflections("");
		//			clazz = java.lang.Class.forName(className);
		//
		//			Package p = Package.getPackage(clazz.getPackage().getName());
		//
		//			Set<Class<?>> subTypes = reflections.getSubTypesOf(clazz.class);
		//
		//		} catch (ClassNotFoundException e) {
		//			e.printStackTrace();
		//		}

		return "";

	}

}
