package de.cognicrypt.codegenerator.generator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.Set;

import crypto.rules.CryptSLPredicate;
import crypto.rules.CryptSLRule;

public class RuleDependencyTree {

	List<CryptSLRule> cryslRules;

	List<String> nodes;

	Map<String, Set<String>> ruleToPred;
	List<Entry<Entry<String, String>, String>> edges;

	public RuleDependencyTree(List<CryptSLRule> rules) {
		nodes = new ArrayList<String>();
		edges = new ArrayList<Entry<Entry<String, String>, String>>();
		ruleToPred = new HashMap<String, Set<String>>();


		for (CryptSLRule rule : rules) {
			nodes.add(rule.getClassName());
			for (CryptSLPredicate pred : rule.getPredicates()) {
				if (!pred.isNegated()) {
					Set<String> predsForRule = ruleToPred.get(pred.getPredName());
					if (predsForRule == null) {
						ruleToPred.put(pred.getPredName(), new HashSet<String>());
						predsForRule = ruleToPred.get(pred.getPredName());
					}
					predsForRule.add(rule.getClassName());
				}
			}
		}

		for (CryptSLRule rule : rules) {
			for (CryptSLPredicate pred : rule.getRequiredPredicates()) {
				Set<String> allPreds = ruleToPred.get(pred.getPredName());
				if (allPreds == null) {
					continue;
				}
				for (String a : allPreds) {
					if (a != null) {
						edges.add(new SimpleEntry<Entry<String,String>, String>(new SimpleEntry<String, String>(a, rule.getClassName()), pred.getPredName()));
					}
				}
			}
		}
	}

	public void toDotFile(String rulesFolder) {
		StringBuilder dotFileSB = new StringBuilder("digraph F {\n");
		for (Entry<Entry<String, String>, String> edge : edges) {
			Entry<String, String> nodes = edge.getKey();
			dotFileSB.append(nodes.getValue());
			dotFileSB.append(" -> ");
			dotFileSB.append(nodes.getKey());
			dotFileSB.append(" [ label=\"depends on\"];\n");
		}
		dotFileSB.append("}");
		File dotFile = new File(rulesFolder + "\\crysldependencies.dot");
		try {
			dotFile.createNewFile();
			Files.write(dotFile.toPath(), dotFileSB.toString().getBytes("UTF-8"));
		} catch (IOException e) {

		}
	}
	/*
	 * digraph F { 
	 * pre_init[shape = rarrow] 
	 * 2[shape = doublecircle] 
	 * pre_init -> 0 [label="javax.crypto.KeyGenerator.getInstan"]; 
	 * 0 -> 1 [label="javax.crypto.KeyGenerator.init(int)"]; 
	 * 1 -> 2 [label="javax.crypto.KeyGenerator.generateK"]; 
	 * 0 -> 2 [label="javax.crypto.KeyGenerator.generateK"]; }
	 */
	
	public boolean hasPath(String start, String goal) {
		Set<String> visited = new HashSet<String>();
		visited.add(start);
		Set<String> toBeVisited = new HashSet<String>();
		toBeVisited.add(start);
		
		
		while(visited.size() != nodes.size()) {
			Set<String> rights = new HashSet<String>(); 
			for (String node : toBeVisited) {
				for (Entry<Entry<String, String>, String> edge : edges) {
					Entry<String, String> nodes = edge.getKey();
					String right = nodes.getValue();
					if (nodes.getKey().equals(node) && !visited.contains(right)) {
						rights.add(right);
					} 
				}
				if (rights.contains(goal)) {
					return true;
				} 
			}
			toBeVisited = rights;
			visited.addAll(rights);
		}
		
		return false;
	}
	
	public List<Entry<Entry<String, String>, String>> getOutgoingEdges(String node) {
		return edges.stream().filter(new Predicate<Entry<Entry<String, String>, String>>() {

			@Override
			public boolean test(Entry<Entry<String, String>, String> entry) {
				return entry.getKey().getKey().equals(node);
			}}).collect(Collectors.toList());
	}
}
