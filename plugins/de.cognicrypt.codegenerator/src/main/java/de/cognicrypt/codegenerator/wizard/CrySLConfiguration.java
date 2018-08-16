package de.cognicrypt.codegenerator.wizard;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import crypto.rules.CryptSLRule;
import de.cognicrypt.codegenerator.question.Answer;
import de.cognicrypt.codegenerator.question.Question;


public class CrySLConfiguration extends Configuration {

	private final List<List<CryptSLRule>> rules;

	protected CrySLConfiguration(List<List<CryptSLRule>> rules2, Map<Question, Answer> constraints, String pathOnDisk) {
		super(constraints, pathOnDisk);
		this.rules = rules2;
	}

	@Override
	public File persistConf() throws IOException {

		return null;
	}

	@Override
	public List<String> getProviders() {
		// TODO Auto-generated method stub
		return null;
	}

	public List<List<CryptSLRule>> getRules() {
		return rules;
	}
}
