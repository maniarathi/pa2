package cs224n.assignment;

import cs224n.assignment.Grammar.BinaryRule;
import cs224n.assignment.Grammar.UnaryRule;
import cs224n.ling.Tree;
import cs224n.util.Pair;
import cs224n.util.Triplet;

import java.util.*;

/**
 * The CKY PCFG Parser you will implement.
 */
public class PCFGParser implements Parser {
	private Grammar grammar;
	private Lexicon lexicon;
	private Set<String> allNonTerminals;
	private List<List<Map<String,Double>>> score;
	private List<List<Map<String,String>>> back;
	//private Map<Triplet<Integer, Integer, String>, Double> score;
	//private Map<Triplet<Integer, Integer, String>, String> back;

	private Tree<String> buildTree(List<String> sentence,
			List<List<Map<String,Double>>> score,
			List<List<Map<String,String>>> back,
			Integer startIndex, Integer endIndex) {
		Tree<String> returnTree = new Tree<String>("ROOT");
		returnTree.setChildren(buildTreeHelper(sentence, score, back,
				startIndex, endIndex, "ROOT"));
		return returnTree;

	}

	private List<Tree<String>> buildTreeHelper(List<String> sentence,
			List<List<Map<String,Double>>> score,
			List<List<Map<String,String>>> back,
			Integer startIndex, Integer endIndex,
			String root) {
		List<Tree<String>> returnTrees = new ArrayList<Tree<String>>();
		// Get the back pointer
		String child = back.get(startIndex).get(endIndex).get(root);
		// Parse children
		if (child == null) {
			// Leaf
			Tree<String> childTree = new Tree<String>(sentence.get(startIndex));
			returnTrees.add(childTree);
		} else if (child.contains("\t")) {
			// Binary rule
			String[] tokens = child.split("\t");
			Integer split = Integer.parseInt(tokens[0]);
			Tree<String> left = new Tree<String>(tokens[1]);
			left.setChildren(buildTreeHelper(sentence,score,back,startIndex,split,tokens[1]));
			Tree<String> right = new Tree<String>(tokens[2]);
			right.setChildren(buildTreeHelper(sentence,score,back,split,endIndex,tokens[2]));
			returnTrees.add(left);
			returnTrees.add(right);
		} else {
			// Unary rule
			Tree<String> childTree = new Tree<String>(child);
			childTree.setChildren(buildTreeHelper(sentence,score,back,startIndex,endIndex,child));
			returnTrees.add(childTree);
		}
		return returnTrees;
	}

	private void getLexiconRules(List<String> sentence) {
		for (int i = 0; i < sentence.size(); i++) {
			// Iterate through nonterminals A
			Set<String> tags = lexicon.getAllTags();
			Set<String> usedTagsForUnaryHandling = new HashSet<String>();
			for (String tag : tags) {
				if (lexicon.scoreTagging(sentence.get(i), tag) > 0) {
					score.get(i).get(i+1).put(tag, lexicon.scoreTagging(sentence.get(i), tag));
					usedTagsForUnaryHandling.add(tag);
				}
			}
			// Handle unaries
			boolean added = true;
			while (added) {
				added = false;
				// Iterate through nonterminals A, B where B is a terminal
				for (String tag : usedTagsForUnaryHandling) {
					List<UnaryRule> rules = grammar.getUnaryRulesByChild(tag);
					for (UnaryRule rule : rules) {
						Double prob = lexicon
								.scoreTagging(sentence.get(i), tag)
								* rule.getScore();
						if (prob > score.get(i).get(i+1).get(rule.getParent())) {
							score.get(i).get(i+1).put(rule.getParent(), prob);
							back.get(i).get(i+1).put(rule.getParent(), rule.getChild());
							added = true;
						}
					}
				}
			}
		}
	}
	
	private void getMultiWordRules(List<String> sentence) {
		for (int span = 2; span <= sentence.size(); span++) {
			for (int begin = 0; begin <= sentence.size() - span; begin++) {
				int end = begin + span;
				for (int split = begin + 1; split <= end - 1; split++) {
					// Get B terminals
					Map<String,Double> Bs = score.get(begin).get(split);
					// Get C terminals
					Map<String,Double> Cs = score.get(split).get(end);
					// Iterate through each combination
					for (String B : Bs.keySet()) {
						// Get grammar rules
						List<BinaryRule> BRules = grammar.getBinaryRulesByLeftChild(B);
						for (BinaryRule rule : BRules) {
							if (Cs.containsKey(rule.getRightChild())) {
								String C = rule.getRightChild();
								// We found an A->BC rules
								double prob = Bs.get(B)*Cs.get(C)*rule.getScore();
								if (prob > score.get(begin).get(end).get(rule.getParent())) {
									score.get(begin).get(end).put(rule.getParent(), prob);
									String backString = Integer.toString(split) + "\t" + B + "\t" + C;
									back.get(begin).get(end).put(rule.getParent(), backString);
								}
							}
						}
					}
					// Handle unaries
					boolean added = true;
					while (added) {
						added = false;
						Map<String,Double> newRules = score.get(begin).get(end);
						for (String child : newRules.keySet()) {
							// Get unary rules
							List<UnaryRule> rules = grammar.getUnaryRulesByChild(child);
							for (UnaryRule rule : rules) {
								double prob = newRules.get(child)*rule.getScore();
								if (prob > score.get(begin).get(end).get(rule.getParent())) {
									score.get(begin).get(end).put(rule.getParent(), prob);
									String backString = child;
									back.get(begin).get(end).put(rule.getParent(), child);
									added = true;
								}
							}
						}
					}
				}
			}
		}
	}
	
	public void train(List<Tree<String>> trainTrees) {
		System.out.println("Begin training...");
		long startTime = System.currentTimeMillis();
		List<Tree<String>> binarizedTrainTrees = new ArrayList<Tree<String>>();
		for (Tree<String> tree : trainTrees) {
			binarizedTrainTrees.add(TreeAnnotations.annotateTree(tree));
		}
		lexicon = new Lexicon(binarizedTrainTrees);
		grammar = new Grammar(binarizedTrainTrees);
		double time = (System.currentTimeMillis() - startTime) / 1000.0;
		System.out.println("Done training... Time to complete = " + time);
	}

	public Tree<String> getBestParse(List<String> sentence) {
		// Get all the nonterminals
		allNonTerminals = new HashSet<String>();
		// First get nonterminals from unary rules from grammar
		for (String childIndex: grammar.unaryRulesByChild.keySet()) {
			for (UnaryRule rule : grammar.getUnaryRulesByChild(childIndex)) {
				allNonTerminals.add(rule.getChild());
				allNonTerminals.add(rule.getParent());
			}
		}
		// Next get all nonterminals from binary rules from grammar
		for (String childIndex : grammar.binaryRulesByLeftChild.keySet()) {
			for (BinaryRule rule : grammar.getBinaryRulesByLeftChild(childIndex)) {
				allNonTerminals.add(rule.getParent());
				allNonTerminals.add(rule.getLeftChild());
				allNonTerminals.add(rule.getRightChild());
			}
		}
		
		long startTime = System.currentTimeMillis();
		System.out.println("Beginning finding the best parse...");
		// INITIALIZE SCORE AND BACK
		score = new ArrayList<List<Map<String,Double>>>();
		back = new ArrayList<List<Map<String,String>>>();
		for (int i = 0; i <= sentence.size(); i++) {
			List<Map<String,Double>> newScoreList = new ArrayList<Map<String,Double>>();
			List<Map<String,String>> newBackList = new ArrayList<Map<String,String>>();
			for (int j = 0; j <= sentence.size(); j++) {
				Map<String,Double> newScoreMap = new HashMap<String,Double>();
				Map<String,String> newBackMap = new HashMap<String,String>();
				for (String k : allNonTerminals) {
					newScoreMap.put(k, 0.0);
					newBackMap.put(k, null);
				}
				newScoreList.add(newScoreMap);
				newBackList.add(newBackMap);
			}
			score.add(newScoreList);
			back.add(newBackList);
		}
		
		
		System.out.println("Starting with lexicon...");
		getLexiconRules(sentence);
		double time = (System.currentTimeMillis() - startTime) / 1000.0;
		startTime = System.currentTimeMillis();
		System.out.println("Single words took time: " + time);
		System.out.println("Starting with multi-word analysis...");
		getMultiWordRules(sentence);
		time = (System.currentTimeMillis() - startTime) / 1000.0;
		System.out.println("Multiwords took time: " + time);
		return TreeAnnotations.unAnnotateTree(buildTree(sentence, score, back,
				0, sentence.size()));
	}
}
