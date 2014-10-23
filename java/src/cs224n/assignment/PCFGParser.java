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

	private Tree<String> buildTree(List<String> sentence,
			Map<Triplet<Integer, Integer, String>, Double> score,
			Map<Triplet<Integer, Integer, String>, String> back,
			Integer startIndex, Integer endIndex) {
		Triplet<Integer, Integer, String> root = new Triplet<Integer, Integer, String>(
				startIndex, endIndex, "ROOT");
		Tree<String> returnTree = new Tree<String>("ROOT");
		returnTree.setChildren(buildTreeHelper(sentence, score, back,
				startIndex, endIndex, root));
		return returnTree;

	}

	private List<Tree<String>> buildTreeHelper(List<String> sentence,
			Map<Triplet<Integer, Integer, String>, Double> score,
			Map<Triplet<Integer, Integer, String>, String> back,
			Integer startIndex, Integer endIndex,
			Triplet<Integer, Integer, String> root) {
		// Get back pointer
		String backPointer = null;
		if (back.containsKey(root)) {
			backPointer = back.get(root);
		} else {
			Tree<String> child = new Tree<String>(sentence.get(startIndex));
			List<Tree<String>> children = new ArrayList<Tree<String>>();
			children.add(child);
			return children;
		}
		// TODO: Find the backpointer with highest probability
		if (!backPointer.contains("\t")) {
			// Handle unaries
			// Create one tree with root as root, then call recursively on same
			// span for children
			Tree<String> tree = new Tree<String>(backPointer);
			Triplet<Integer, Integer, String> newSearchTriplet = new Triplet<Integer, Integer, String>(
					startIndex, endIndex, backPointer);
			List<Tree<String>> children = buildTreeHelper(sentence, score,
					back, startIndex, endIndex, newSearchTriplet);
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
			String leftRoot = tokens[1];
			String rightRoot = tokens[2];
			// Get left tree
			Tree<String> left = new Tree<String>(leftRoot);
			Integer mid = split;
			Triplet<Integer, Integer, String> nextLeft = new Triplet<Integer, Integer, String>(
					startIndex, mid, leftRoot);
			List<Tree<String>> leftChild = buildTreeHelper(sentence, score,
					back, startIndex, split, nextLeft);
			if (leftChild != null) {
				left.setChildren(leftChild);
			}
			// Get right tree
			Tree<String> right = new Tree<String>(rightRoot);
			Triplet<Integer, Integer, String> nextRight = new Triplet<Integer, Integer, String>(
					mid, endIndex, rightRoot);
			List<Tree<String>> rightChild = buildTreeHelper(sentence, score,
					back, split, endIndex, nextRight);
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
		System.out.println("Begin training...");
		long startTime = System.currentTimeMillis();
		List<Tree<String>> binarizedTrainTrees = new ArrayList<Tree<String>>();
		for (Tree<String> tree : trainTrees) {
			binarizedTrainTrees.add(TreeAnnotations.annotateTree(tree));
		}
		lexicon = new Lexicon(trainTrees);
		grammar = new Grammar(binarizedTrainTrees);
		// System.out.println(grammar.toString());
		double time = (System.currentTimeMillis() - startTime) / 1000.0;
		System.out.println("Done training... Time to complete = " + time);
	}

	public Tree<String> getBestParse(List<String> sentence) {
		long startTime = System.currentTimeMillis();
		System.out.println("Beginning finding the best parse...");
		Map<Triplet<Integer, Integer, String>, Double> score = new HashMap<Triplet<Integer, Integer, String>, Double>();
		Map<Triplet<Integer, Integer, String>, String> back = new HashMap<Triplet<Integer, Integer, String>, String>();
		System.out.println("Starting with lexicon...");
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
				Set<String> freshTags = new HashSet<String>();
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
								back.put(newKey, rule.getChild());
								freshTags.add(newKey.getThird());
								added = true;
							}
						} else {
							score.put(newKey, prob);
							back.put(newKey, rule.getChild());
							freshTags.add(newKey.getThird());
							added = true;
						}
					}
				}
				usedTagsForUnaryHandling = freshTags;
			}
		}
		double time = (System.currentTimeMillis() - startTime) / 1000.0;
		startTime = System.currentTimeMillis();
		System.out.println("Single words took time: " + time);
		System.out.println("Starting with multi-word analysis...");
		for (int span = 2; span <= sentence.size(); span++) {
			time = (System.currentTimeMillis() - startTime) / 1000.0;
			System.out.println("Span = " + span + " Time = " + time);
			for (int begin = 0; begin <= (sentence.size() - span); begin++) {
				int end = begin + span;
				for (int split = begin + 1; split <= (end - 1); split++) {
					List<Triplet<Integer, Integer, String>> newlyAdded = new ArrayList<Triplet<Integer, Integer, String>>();
					long subStartTime = System.currentTimeMillis();
					// Iterate through nonterminals A, B, C
					List<String> Bs = new ArrayList<String>();
					List<String> Cs = new ArrayList<String>();
					for (Triplet<Integer, Integer, String> triple : score
							.keySet()) {
						if (triple.getFirst() == begin
								&& triple.getSecond() == split) {
							Bs.add(triple.getThird());
						}
						if (triple.getFirst() == split
								&& triple.getSecond() == end) {
							Cs.add(triple.getThird());
						}
					}
					// Iterate through B's
					for (String B : Bs) {
						// Get rules
						List<BinaryRule> rulesWithBOnLeftChild = grammar
								.getBinaryRulesByLeftChild(B);
						for (BinaryRule rule : rulesWithBOnLeftChild) {
							for (String C : Cs) {
								if (rule.getRightChild() == C) {
									// We found a rule of the form A -> BC
									Triplet<Integer, Integer, String> BTriplet = new Triplet<Integer, Integer, String>(
											begin, split, B);
									Triplet<Integer, Integer, String> CTriplet = new Triplet<Integer, Integer, String>(
											split, end, C);
									Double prob = score.get(BTriplet)
											* score.get(CTriplet)
											* rule.getScore();
									Triplet<Integer, Integer, String> newKey = new Triplet<Integer, Integer, String>(
											begin, end, rule.getParent());

									if (score.containsKey(newKey)) {
										// Only update if prob is bigger than
										// existing one
										if (score.get(newKey) < prob) {
											if (!newlyAdded.contains(newKey)) {
												newlyAdded.add(newKey);
											}
											score.put(newKey, prob);
											String newTriple = Integer
													.toString(split)
													+ "\t"
													+ B
													+ "\t" + C;
											back.put(newKey, newTriple);
										}
									} else {
										if (!newlyAdded.contains(newKey)) {
											newlyAdded.add(newKey);
										}
										score.put(newKey, prob);
										String newTriple = Integer
												.toString(split)
												+ "\t"
												+ B
												+ "\t" + C;
										back.put(newKey, newTriple);
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
						List<Triplet<Integer, Integer, String>> freshTags = new ArrayList<Triplet<Integer, Integer, String>>();
						for (Triplet<Integer, Integer, String> triple : newlyAdded) {
							// Get rules
							List<UnaryRule> rules = grammar
									.getUnaryRulesByChild(triple.getThird());
							for (UnaryRule rule : rules) {
								Double prob = rule.getScore()
										* score.get(triple);
								Triplet<Integer, Integer, String> withParent = new Triplet<Integer, Integer, String>(
										triple.getFirst(), triple.getSecond(),
										rule.getParent());
								if (score.containsKey(withParent)) {
									if (score.get(withParent) < prob) {
										score.put(withParent, prob);
										freshTags.add(withParent);
										Triplet<Integer, Integer, String> keyForBack = new Triplet<Integer, Integer, String>(
												begin, end, rule.getParent());
										back.put(keyForBack, rule.getChild());
										added = true;
									}
								} else {
									score.put(withParent, prob);
									freshTags.add(withParent);
									Triplet<Integer, Integer, String> keyForBack = new Triplet<Integer, Integer, String>(
											begin, end, rule.getParent());
									back.put(keyForBack, rule.getChild());
									added = true;
								}
							}
						}
						newlyAdded = freshTags;
					}
				}
			}
		}
		time = (System.currentTimeMillis() - startTime) / 1000.0;
		System.out.println("Multiwords took time: " + time);
		for (Triplet<Integer, Integer, String> a : score.keySet()) {
			System.out.println(a.toString() + "\t" + score.get(a));
		}
		System.out.println();
		for (Triplet<Integer, Integer, String> b : back.keySet()) {
			System.out.println(b.toString() + "\t" + back.get(b));
		}
		// System.out.println("That was the final score.. this is the final back");
		// System.out.println(back.toString());
		return TreeAnnotations.unAnnotateTree(buildTree(sentence, score, back,
				0, sentence.size()));
	}
}
