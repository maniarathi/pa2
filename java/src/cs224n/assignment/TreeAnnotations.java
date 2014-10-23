package cs224n.assignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cs224n.ling.Tree;
import cs224n.ling.Trees;
import cs224n.ling.Trees.MarkovizationAnnotationStripper;
import cs224n.util.Filter;

/**
 * Class which contains code for annotating and binarizing trees for
 * the parser's use, and debinarizing and unannotating them for
 * scoring.
 */
public class TreeAnnotations {
	
	public static Tree<String> annotateTree(Tree<String> unAnnotatedTree) {

		// Currently, the only annotation done is a lossless binarization

		// TODO: change the annotation from a lossless binarization to a
		// finite-order markov process (try at least 1st and 2nd order)

		// TODO : mark nodes with the label of their parent nodes, giving a second
		// order vertical markov process

		return binarizeTree(unAnnotatedTree);

	}

	private static Tree<String> verticalMarkovizationOrder2(Tree<String> tree, String parent) {
		Tree<String> returnTree;
		if (parent != null) {
			returnTree = new Tree<String>(tree.getLabel());
			List<Tree<String>> returnTreeChildren = new ArrayList<Tree<String>>();
			for (Tree<String> child : tree.getChildren()) {
				returnTreeChildren.add(verticalMarkovizationOrder2(child,tree.getLabel()));
			}
			returnTree.setChildren(returnTreeChildren);
		} else {
			String newLabel = tree.getLabel()+"^"+parent;
			returnTree = new Tree<String>(newLabel);
			List<Tree<String>> returnTreeChildren = new ArrayList<Tree<String>>();
			for (Tree<String> child : tree.getChildren()) {
				returnTreeChildren.add(verticalMarkovizationOrder2(child,tree.getLabel()));
			}
			returnTree.setChildren(returnTreeChildren);
		}
		return returnTree;
	}
	
	private static Tree<String> verticalMarkovizationOrder3(Tree<String> tree, String parent, String grandparent) {
		String label = "";
		// Create new label
		if (parent != null) {
			if (grandparent != null) {
				// Grandparent is not null, parent is not null
				label = tree.getLabel()+"^"+parent+"^"+grandparent;
			} else {
				// Parent is not null, but grandparent is null
				label = tree.getLabel()+"^"+parent;
			}
		} else {
			// Parent is null so grandparent is also null
			label = tree.getLabel();
		}
		Tree<String> returnTree = new Tree<String>(label);
		// Handle children
		List<Tree<String>> returnTreeChildren = new ArrayList<Tree<String>>();
		for (Tree<String> child : tree.getChildren()) {
			returnTreeChildren.add(verticalMarkovizationOrder3(child,tree.getLabel(),parent));
		}
		returnTree.setChildren(returnTreeChildren);
		return returnTree;
	}
	
	private static Tree<String> binarizeTree(Tree<String> tree) {
		String label = tree.getLabel();
		if (tree.isLeaf())
			return new Tree<String>(label);
		if (tree.getChildren().size() == 1) {
			return new Tree<String>
			(label, 
					Collections.singletonList(binarizeTree(tree.getChildren().get(0))));
		}
		// otherwise, it's a binary-or-more local tree, 
		// so decompose it into a sequence of binary and unary trees.
		String intermediateLabel = "@"+label+"->";
		Tree<String> intermediateTree =
				binarizeTreeHelper(tree, 0, intermediateLabel);
		return new Tree<String>(label, intermediateTree.getChildren());
	}

	private static Tree<String> binarizeTreeHelper(Tree<String> tree,
			int numChildrenGenerated, 
			String intermediateLabel) {
		Tree<String> leftTree = tree.getChildren().get(numChildrenGenerated);
		List<Tree<String>> children = new ArrayList<Tree<String>>();
		children.add(binarizeTree(leftTree));
		if (numChildrenGenerated == tree.getChildren().size() - 2) {
			Tree<String> rightTree = tree.getChildren().get(numChildrenGenerated + 1);
			children.add(binarizeTree(rightTree));
		} else if (numChildrenGenerated < tree.getChildren().size() - 1) {
			Tree<String> rightTree = 
					binarizeTreeHelper(tree, numChildrenGenerated + 1, 
							intermediateLabel + "_" + leftTree.getLabel());
			children.add(rightTree);
		}
		return new Tree<String>(intermediateLabel, children);
	} 

	public static Tree<String> unAnnotateTree(Tree<String> annotatedTree) {

		// Remove intermediate nodes (labels beginning with "@"
		// Remove all material on node labels which follow their base symbol
		// (cuts at the leftmost - or ^ character)
		// Examples: a node with label @NP->DT_JJ will be spliced out, 
		// and a node with label NP^S will be reduced to NP

		Tree<String> debinarizedTree =
				Trees.spliceNodes(annotatedTree, new Filter<String>() {
					public boolean accept(String s) {
						return s.startsWith("@");
					}
				});
		Tree<String> unAnnotatedTree = 
				(new Trees.FunctionNodeStripper()).transformTree(debinarizedTree);
    Tree<String> unMarkovizedTree =
        (new Trees.MarkovizationAnnotationStripper()).transformTree(unAnnotatedTree);
		return unMarkovizedTree;
	}
}
