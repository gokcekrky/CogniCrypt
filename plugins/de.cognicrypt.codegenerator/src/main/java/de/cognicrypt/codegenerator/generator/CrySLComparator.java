package de.cognicrypt.codegenerator.generator;

import java.util.Comparator;
import java.util.List;

import crypto.rules.CryptSLRule;


public class CrySLComparator implements Comparator<String> {
	private final RuleDependencyTree rdt;
	
	public CrySLComparator(List<CryptSLRule> list) {
		rdt = new RuleDependencyTree(list);
	}
	
	@Override
	public int compare(String left, String right) {
		if (rdt.hasPath(left, right)) {
			return -1;
		} else if (rdt.hasPath(right, left)) {
			return 1;
		} else {
			return 0;
		}
	}

}
