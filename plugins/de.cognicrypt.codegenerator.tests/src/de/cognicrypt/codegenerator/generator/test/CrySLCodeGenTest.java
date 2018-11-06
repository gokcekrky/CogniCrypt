package de.cognicrypt.codegenerator.generator.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.junit.Test;

import crypto.rules.CryptSLRule;
import de.cognicrypt.codegenerator.Activator;
import de.cognicrypt.codegenerator.DeveloperProject;
import de.cognicrypt.codegenerator.generator.CodeGenerator;
import de.cognicrypt.codegenerator.generator.CrySLBasedCodeGenerator;
import de.cognicrypt.codegenerator.testutilities.TestUtils;
import de.cognicrypt.codegenerator.wizard.CrySLConfiguration;
import de.cognicrypt.utils.Utils;

public class CrySLCodeGenTest {

	@Test
	public void generatePBEEnc() {

		List<List<CryptSLRule>> rules = new ArrayList<List<CryptSLRule>>();
		try {
			IJavaProject testJavaProject = TestUtils.createJavaProject("TestProject_ENC");
			TestUtils.generateJavaClassInJavaProject(testJavaProject, "testPackage", "Test");
			CodeGenerator codeGenerator = new CrySLBasedCodeGenerator(testJavaProject.getProject());
			DeveloperProject developerProject = codeGenerator.getDeveloperProject();

			List<List<String>> stringRules = new ArrayList<List<String>>();
			stringRules.add(Arrays.asList(
					new String[] { "SecureRandom", "PBEKeySpec", "SecretKeyFactory", "SecretKey", "SecretKeySpec" }));
			stringRules.add(Arrays.asList(new String[] { "Cipher" }));

			for (List<String> rule : stringRules) {
				ArrayList<CryptSLRule> newRules = new ArrayList<CryptSLRule>();
				rules.add(newRules);
				for (String r : rule) {
					try {
						newRules.add(Utils.getCryptSLRule(r));
					} catch (FileNotFoundException ex) {
						Activator.getDefault().logError(ex, "CrySL rule" + r + " not found.");
					}
				}
			}

			CrySLConfiguration codeGenConfig = TestUtils.createCrySLConfigurationForCodeGeneration(developerProject,
					rules);
			boolean encCheck = codeGenerator.generateCodeTemplates(codeGenConfig, null);
			assertTrue(encCheck);

			ICompilationUnit testClassUnit = TestUtils.getICompilationUnit(developerProject, "Crypto", "Output.java");
			TestUtils.openJavaFileInWorkspace(developerProject, "Crypto", testClassUnit);
			assertEquals(1, TestUtils.countMethods(testClassUnit));

			ICompilationUnit encClassUnit = TestUtils.getICompilationUnit(developerProject, "Crypto",
					"CogniCryptCipher.java");
			TestUtils.openJavaFileInWorkspace(developerProject, "Crypto", encClassUnit);
			assertEquals(1, TestUtils.countMethods(encClassUnit));

			ICompilationUnit keyClassUnit = TestUtils.getICompilationUnit(developerProject, "testPackage",
					"CogniCryptSecretKeySpec.java");
			TestUtils.openJavaFileInWorkspace(developerProject, "Crypto", keyClassUnit);
			assertEquals(1, TestUtils.countMethods(keyClassUnit));

		} catch (Exception ex) {

		}

	}

	@Test
	public void generateSymEnc() {

		List<List<CryptSLRule>> rules = new ArrayList<List<CryptSLRule>>();
		try {
			IJavaProject testJavaProject = TestUtils.createJavaProject("TestProject_PBEENC");
			TestUtils.generateJavaClassInJavaProject(testJavaProject, "testPackage", "Test");
			CodeGenerator codeGenerator = new CrySLBasedCodeGenerator(testJavaProject.getProject());
			DeveloperProject developerProject = codeGenerator.getDeveloperProject();

			List<List<String>> stringRules = new ArrayList<List<String>>();
			stringRules.add(Arrays.asList(new String[] { "KeyGenerator" }));
			stringRules.add(Arrays.asList(new String[] { "Cipher" }));

			for (List<String> rule : stringRules) {
				ArrayList<CryptSLRule> newRules = new ArrayList<CryptSLRule>();
				rules.add(newRules);
				for (String r : rule) {
					try {
						newRules.add(Utils.getCryptSLRule(r));
					} catch (FileNotFoundException ex) {
						Activator.getDefault().logError(ex, "CrySL rule" + r + " not found.");
					}
				}
			}

			CrySLConfiguration codeGenConfig = TestUtils.createCrySLConfigurationForCodeGeneration(developerProject,
					rules);
			boolean encCheck = codeGenerator.generateCodeTemplates(codeGenConfig, null);
			assertTrue(encCheck);

			ICompilationUnit testClassUnit = TestUtils.getICompilationUnit(developerProject, "Crypto", "Output.java");
			TestUtils.openJavaFileInWorkspace(developerProject, "Crypto", testClassUnit);
			assertEquals(1, TestUtils.countMethods(testClassUnit));

			ICompilationUnit encClassUnit = TestUtils.getICompilationUnit(developerProject, "Crypto",
					"CogniCryptCipher.java");
			TestUtils.openJavaFileInWorkspace(developerProject, "Crypto", encClassUnit);
			assertEquals(1, TestUtils.countMethods(encClassUnit));

			ICompilationUnit keyClassUnit = TestUtils.getICompilationUnit(developerProject, "testPackage",
					"CogniCryptKeyGenerator.java");
			TestUtils.openJavaFileInWorkspace(developerProject, "Crypto", keyClassUnit);
			assertEquals(1, TestUtils.countMethods(keyClassUnit));

		} catch (Exception ex) {

		}

	}

	public void generatePWD() {

		List<List<CryptSLRule>> rules = new ArrayList<List<CryptSLRule>>();
		try {
			IJavaProject testJavaProject = TestUtils.createJavaProject("TestProject_ENC");
			TestUtils.generateJavaClassInJavaProject(testJavaProject, "testPackage", "Test");
			CodeGenerator codeGenerator = new CrySLBasedCodeGenerator(testJavaProject.getProject());
			DeveloperProject developerProject = codeGenerator.getDeveloperProject();

			List<List<String>> stringRules = new ArrayList<List<String>>();
			stringRules.add(Arrays.asList(new String[] { "SecureRandom", "PBEKeySpec", "SecretKeyFactory" }));
			for (List<String> rule : stringRules) {
				ArrayList<CryptSLRule> newRules = new ArrayList<CryptSLRule>();
				rules.add(newRules);
				for (String r : rule) {
					try {
						newRules.add(Utils.getCryptSLRule(r));
					} catch (FileNotFoundException ex) {
						Activator.getDefault().logError(ex, "CrySL rule" + r + " not found.");
					}
				}
			}

			CrySLConfiguration codeGenConfig = TestUtils.createCrySLConfigurationForCodeGeneration(developerProject,
					rules);
			boolean encCheck = codeGenerator.generateCodeTemplates(codeGenConfig, null);
			assertTrue(encCheck);

			ICompilationUnit testClassUnit = TestUtils.getICompilationUnit(developerProject, "Crypto", "Output.java");
			TestUtils.openJavaFileInWorkspace(developerProject, "Crypto", testClassUnit);
			assertEquals(1, TestUtils.countMethods(testClassUnit));

			ICompilationUnit encClassUnit = TestUtils.getICompilationUnit(developerProject, "Crypto",
					"CogniCryptCipher.java");
			TestUtils.openJavaFileInWorkspace(developerProject, "Crypto", encClassUnit);
			assertEquals(1, TestUtils.countMethods(encClassUnit));

			ICompilationUnit keyClassUnit = TestUtils.getICompilationUnit(developerProject, "testPackage",
					"CogniCryptSecretKeySpec.java");
			TestUtils.openJavaFileInWorkspace(developerProject, "Crypto", keyClassUnit);
			assertEquals(1, TestUtils.countMethods(keyClassUnit));

		} catch (Exception ex) {

		}

	}

}
