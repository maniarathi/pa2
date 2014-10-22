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

	private Tree<String> buildTree(
			Map<Triplet<Integer, Integer, String>, Double> score,
			Map<Triplet<Integer, Integer, String>, List<String>> back,
			Integer startIndex, Integer endIndex) {

		Map<Triplet<Integer, Integer, String>, Double> possibleRoots = new HashMap<Triplet<Integer, Integer, String>, Double>();
		// Find elements that cover entire span
		for (Triplet<Integer, Integer, String> triple : score.keySet()) {
			if (triple.getFirst() == startIndex
					&& triple.getSecond() == endIndex) {
				possibleRoots.put(triple, score.get(triple));
			}
		}
		// Get max-es of those entire span elements
		Double maxProb = 0.0;
		List<Triplet<Integer, Integer, String>> maxTriple = new ArrayList<Triplet<Integer, Integer, String>>();
		for (Triplet<Integer, Integer, String> triple : possibleRoots.keySet()) {
			score.remove(triple);
			if (possibleRoots.get(triple) >= maxProb) {
				maxProb = possibleRoots.get(triple);
				maxTriple.add(triple);
			}
		}
		System.out.println(maxTriple.toString());
		// Create new tree (giving preference to ROOT if it exists)
		Triplet<Integer, Integer, String> chosenRoot = null;
		List<Triplet<Integer, Integer, String>> possible = new ArrayList<Triplet<Integer, Integer, String>>();
		boolean foundRoot = false;
		for (Triplet<Integer, Integer, String> label : maxTriple) {
			for (Triplet<Integer, Integer, String> backNode : back.keySet()) {
				if (label.equals(backNode)) {
					if (label.getThird() == "ROOT") {
						foundRoot = true;
						chosenRoot = label;
					} else {
						if (!foundRoot) {
							possible.add(label);
						}
					}
				}
			}
		}
		if (possible.size() > 0 && !foundRoot) {
			chosenRoot = possible.get(0);
		}
		System.out.println(chosenRoot.toString());
		System.out.println(back.get(chosenRoot).toString());

		Tree<String> returnTree = new Tree<String>(chosenRoot.getThird());
		returnTree.setChildren(buildTreeHelper(score, back, startIndex,
				endIndex, chosenRoot));
		return returnTree;

	}

	private List<Tree<String>> buildTreeHelper(
			Map<Triplet<Integer, Integer, String>, Double> score,
			Map<Triplet<Integer, Integer, String>, List<String>> back,
			Integer startIndex, Integer endIndex,
			Triplet<Integer, Integer, String> root) {
		// Get back pointer
		List<String> backPointers = new ArrayList<String>();
		if (back.containsKey(root)) {
			backPointers = back.get(root);
		} else {
			System.out.println("This is root: " + root.getThird());
			System.out.println("I would print a word here");
			System.out.println(startIndex);
			return null;
		}
		// Find the backpointer with highest probability
		System.out.println("BPs for " + root.getThird());
		for (String bp : backPointers) {
			System.out.println(bp);
		}
		String backPointer = backPointers.get(0);
		if (!backPointer.contains("\t")) {
			// Handle unaries
			// Create one tree with root as root, then call recursively on same
			// span for children
			System.out.println("I AM A UNARY: " + backPointer);
			Tree<String> tree = new Tree<String>(backPointer);
			Triplet<Integer, Integer, String> newSearchTriplet = new Triplet<Integer, Integer, String>(
					startIndex, endIndex, backPointer);
			System.out.println("Unary start index, end index: " + startIndex
					+ "," + endIndex);
			List<Tree<String>> children = buildTreeHelper(score, back,
					startIndex, endIndex, newSearchTriplet);
			if (children != null) {
				tree.setChildren(children);
			}
			List<Tree<String>> returnTrees = new ArrayList<Tree<String>>();
			returnTrees.add(tree);
			return returnTrees;
		} else {
			// Handle binaries
			// Parse the string: first token contains split, second contains
			// left child root, third contains right child root
			String[] tokens = backPointer.split("\t");
			Integer split = Integer.parseInt(tokens[0]);
			System.out.println("SPLIT = " + split);
			String leftRoot = tokens[1];
			String rightRoot = tokens[2];
			System.out.println("I AM A BINARY: " + backPointer);
			// Get left tree
			Tree<String> left = new Tree<String>(leftRoot);
			Integer mid = split;
			Triplet<Integer, Integer, String> nextLeft = new Triplet<Integer, Integer, String>(
					startIndex, mid, leftRoot);
			System.out.println("Left split: " + startIndex + "," + mid);
			List<Tree<String>> leftChild = buildTreeHelper(score, back,
					startIndex, split, nextLeft);
			if (leftChild != null) {
				left.setChildren(leftChild);
			}
			// Get right tree
			Tree<String> right = new Tree<String>(rightRoot);
			Triplet<Integer, Integer, String> nextRight = new Triplet<Integer, Integer, String>(
					mid, endIndex, rightRoot);
			System.out.println("Right split: " + mid + "," + endIndex);
			List<Tree<String>> rightChild = buildTreeHelper(score, back, split,
					endIndex, nextRight);
			if (rightChild != null) {
				right.setChildren(rightChild);
			}
			// Construct tree
			List<Tree<String>> returnTree = new ArrayList<Tree<String>>();
			returnTree.add(left);
			returnTree.add(right);
			return returnTree;
		}
	}

	public void train(List<Tree<String>> trainTrees) {
		List<Tree<String>> binarizedTrainTrees = new ArrayList<Tree<String>>();
		for (Tree<String> tree : trainTrees) {
			binarizedTrainTrees.add(TreeAnnotations.annotateTree(tree));
		}
		lexicon = new Lexicon(trainTrees);
		grammar = new Grammar(binarizedTrainTrees);
		System.out.println(grammar.toString());
	}

	public Tree<String> getBestParse(List<String> sentence) {
		// TODO: implement this method
		System.out.println(sentence.toString());
		Map<Triplet<Integer, Integer, String>, Double> score = new HashMap<Triplet<Integer, Integer, String>, Double>();
		Map<Triplet<Integer, Integer, String>, List<String>> back = new HashMap<Triplet<Integer, Integer, String>, List<String>>();
		System.out.println("Begin handling words only...");
		for (int i = 0; i < sentence.size(); i++) {
			// Iterate through nonterminals A
			Set<String> tags = lexicon.getAllTags();
			Set<String> usedTagsForUnaryHandling = new HashSet<String>();
			for (String tag : tags) {
				if (lexicon.scoreTagging(sentence.get(i), tag) > 0) {
					Triplet<Integer, Integer, String> prob = new Triplet<Integer, Integer, String>(
							i, i + 1, tag);
					score.put(prob, lexicon.scoreTagging(sentence.get(i), tag));
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
						Triplet<Integer, Integer, String> newKey = new Triplet<Integer, Integer, String>(
								i, i + 1, rule.getParent());
						if (score.containsKey(newKey)) {
							if (score.get(newKey) < prob) {
								score.put(newKey, prob);
								if (back.containsKey(newKey)) {
									List<String> vals = back.get(newKey);
									vals.add(rule.getChild());
									back.put(newKey, vals);
								} else {
									List<String> vals = new ArrayList<String>();
									vals.add(rule.getChild());
									back.put(newKey, vals);
								}
								added = true;
							}
						} else {
							score.put(newKey, prob);
							if (back.containsKey(newKey)) {
								List<String> vals = back.get(newKey);
								vals.add(rule.getChild());
								back.put(newKey, vals);
							} else {
								List<String> vals = new ArrayList<String>();
								vals.add(rule.getChild());
								back.put(newKey, vals);
							}
							added = true;
						}
					}
				}
			}
		}
		System.out.println("Finished handling words... now bigger phrases...");
		for (int span = 2; span <= sentence.size(); span++) {
			for (int begin = 0; begin <= (sentence.size() - span); begin++) {
				int end = begin + span;
				List<Triplet<Integer, Integer, String>> newlyAdded = new ArrayList<Triplet<Integer, Integer, String>>();
				for (int split = begin + 1; split <= (end - 1); split++) {
					// Iterate through nonterminals A, B, C
					// Get Triplets with begin,split
					// Get Triplets with split,end
					List<Triplet<Integer, Integer, String>> beginToSplit = new ArrayList<Triplet<Integer, Integer, String>>();
					List<Triplet<Integer, Integer, String>> splitToEnd = new ArrayList<Triplet<Integer, Integer, String>>();
					for (Triplet<Integer, Integer, String> triple : score
							.keySet()) {
						if (triple.getFirst() == begin
								&& triple.getSecond() == split) {
							beginToSplit.add(triple);
						}
						if (triple.getFirst() == split
								&& triple.getSecond() == end) {
							splitToEnd.add(triple);
						}
					}
					// Iterate through B's
					for (Triplet<Integer, Integer, String> triple : beginToSplit) {
						// Get rules
						List<BinaryRule> rulesWithBOnLeftChild = grammar
								.getBinaryRulesByLeftChild(triple.getThird());
						for (BinaryRule rule : rulesWithBOnLeftChild) {
							for (Triplet<Integer, Integer, String> cTriple : beginToSplit) {
								if (rule.getLeftChild() == cTriple.getThird()) {
									// We found a rule of the form A -> BC
									Double prob = score.get(triple)
											* score.get(cTriple)
											* rule.getScore();
									Triplet<Integer, Integer, String> newKey = new Triplet<Integer, Integer, String>(
											begin, end, rule.getParent());
									if (!newlyAdded.contains(newKey)) {
										newlyAdded.add(newKey);
									}
									if (score.containsKey(newKey)) {
										// Only update if prob is bigger than
										// existing one
										if (score.get(newKey) < prob) {
											score.put(newKey, prob);
											String newTriple = Integer
													.toString(split)
													+ "\t"
													+ triple.getThird()
													+ "\t"
													+ cTriple.getThird();
											if (back.containsKey(newKey)) {
												List<String> vals = back
														.get(newKey);
												vals.add(newTriple);
												back.put(newKey, vals);
											} else {
												List<String> vals = new ArrayList<String>();
												vals.add(newTriple);
												back.put(newKey, vals);
											}
										}
									} else {
										score.put(newKey, prob);
										String newTriple = Integer
												.toString(split)
												+ "\t"
												+ triple.getThird()
												+ "\t"
												+ cTriple.getThird();
										if (back.containsKey(newKey)) {
											List<String> vals = back
													.get(newKey);
											vals.add(newTriple);
											back.put(newKey, vals);
										} else {
											List<String> vals = new ArrayList<String>();
											vals.add(newTriple);
											back.put(newKey, vals);
										}
									}
								}
							}
						}
					}
				}
				// Handle unaries
				boolean added = true;
				while (added) {
					added = false;
					// Iterate through nonterminals A, B
					// Iterate through B's
					for (Triplet<Integer, Integer, String> triple : newlyAdded) {
						// Get rules
						List<UnaryRule> rules = grammar
								.getUnaryRulesByChild(triple.getThird());
						for (UnaryRule rule : rules) {
							Double prob = rule.getScore() * score.get(triple);
							Triplet<Integer, Integer, String> withParent = new Triplet<Integer, Integer, String>(
									triple.getFirst(), triple.getSecond(),
									rule.getParent());
							if (score.containsKey(withParent)) {
								if (score.get(withParent) < prob) {
									score.put(withParent, prob);
									Triplet<Integer, Integer, String> keyForBack = new Triplet<Integer, Integer, String>(
											begin, end, rule.getParent());
									if (back.containsKey(keyForBack)) {
										List<String> vals = back
												.get(keyForBack);
										vals.add(rule.getChild());
										back.put(keyForBack, vals);
									} else {
										List<String> vals = new ArrayList<String>();
										vals.add(rule.getChild());
										back.put(keyForBack, vals);
									}
									added = true;
								}
							} else {
								score.put(withParent, prob);
								Triplet<Integer, Integer, String> keyForBack = new Triplet<Integer, Integer, String>(
										begin, end, rule.getParent());
								if (back.containsKey(keyForBack)) {
									List<String> vals = back.get(keyForBack);
									vals.add(rule.getChild());
									back.put(keyForBack, vals);
								} else {
									List<String> vals = new ArrayList<String>();
									vals.add(rule.getChild());
									back.put(keyForBack, vals);
								}
								added = true;
							}
						}
					}
				}
			}
		}
		System.out.println("Final scores: ");
		System.out.println(score.toString());
		System.out.println("Final back: ");
		System.out.println(back.toString());
		return TreeAnnotations.unAnnotateTree(buildTree(score, back, 0, sentence.size()));
	}
}
