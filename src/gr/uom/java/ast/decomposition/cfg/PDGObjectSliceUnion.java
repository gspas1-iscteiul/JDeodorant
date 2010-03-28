package gr.uom.java.ast.decomposition.cfg;

import gr.uom.java.ast.MethodObject;
import gr.uom.java.jdeodorant.preferences.PreferenceConstants;
import gr.uom.java.jdeodorant.refactoring.Activator;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jface.preference.IPreferenceStore;

public class PDGObjectSliceUnion {
	private PDG pdg;
	private List<PDGSliceUnion> sliceUnions;
	private MethodObject method;
	private BasicBlock boundaryBlock;
	private AbstractVariable objectReference;
	private IFile iFile;
	private int methodSize;
	private PDGSlice subgraph;
	private Set<PDGNode> sliceNodes;
	private Set<AbstractVariable> passedParameters;
	private Set<PDGNode> indispensableNodes;
	private Set<PDGNode> removableNodes;
	
	public PDGObjectSliceUnion(PDG pdg, BasicBlock boundaryBlock, List<PDGSliceUnion> sliceUnions,
			AbstractVariable objectReference) {
		this.pdg = pdg;
		this.sliceUnions = sliceUnions;
		this.method = pdg.getMethod();
		this.iFile = pdg.getIFile();
		this.methodSize = pdg.getTotalNumberOfStatements();
		this.boundaryBlock = boundaryBlock;
		this.objectReference = objectReference;
		this.subgraph = sliceUnions.get(0).getSlices().get(0);
		this.sliceNodes = getSliceNodes();
		//key is the branching node and value is the innermost loop node
		Map<PDGNode, PDGNode> branchingNodeMap = getInnerMostLoopNodesForBranchingNodes();
		Set<PDGNode> nodesToBeAddedToSliceDueToBranchingNodes = new TreeSet<PDGNode>();
		for(PDGNode branchingNode : branchingNodeMap.keySet()) {
			PDGNode innerMostLoopNode = branchingNodeMap.get(branchingNode);
			if(sliceNodes.contains(innerMostLoopNode)) {
				CFGNode cfgNode = innerMostLoopNode.getCFGNode();
				if(cfgNode instanceof CFGBranchLoopNode || cfgNode instanceof CFGBranchDoLoopNode) {
					Set<PDGNode> branchingNodeSlice = subgraph.computeSlice(branchingNode);
					nodesToBeAddedToSliceDueToBranchingNodes.addAll(branchingNodeSlice);
				}
			}
		}
		sliceNodes.addAll(nodesToBeAddedToSliceDueToBranchingNodes);
		Set<PDGNode> remainingNodes = new TreeSet<PDGNode>();
		remainingNodes.add(pdg.getEntryNode());
		for(GraphNode node : pdg.nodes) {
			PDGNode pdgNode = (PDGNode)node;
			if(!sliceNodes.contains(pdgNode))
				remainingNodes.add(pdgNode);
		}
		Set<PDGNode> branchingNodesToBeAddedToDuplicatedNodes = new TreeSet<PDGNode>();
		for(PDGNode remainingNode : remainingNodes) {
			for(PDGNode branchingNode : branchingNodeMap.keySet()) {
				PDGNode innerMostLoopNode = branchingNodeMap.get(branchingNode);
				CFGNode cfgNode = innerMostLoopNode.getCFGNode();
				if(cfgNode instanceof CFGBranchLoopNode || cfgNode instanceof CFGBranchDoLoopNode) {
					if(isNestedInside(remainingNode, innerMostLoopNode))
						branchingNodesToBeAddedToDuplicatedNodes.add(branchingNode);
				}
			}
		}
		this.passedParameters = new LinkedHashSet<AbstractVariable>();
		Set<PDGNode> nCD = new LinkedHashSet<PDGNode>();
		Set<PDGNode> nDD = new LinkedHashSet<PDGNode>();
		for(GraphEdge edge : pdg.edges) {
			PDGDependence dependence = (PDGDependence)edge;
			PDGNode srcPDGNode = (PDGNode)dependence.src;
			PDGNode dstPDGNode = (PDGNode)dependence.dst;
			if(dependence instanceof PDGDataDependence) {
				PDGDataDependence dataDependence = (PDGDataDependence)dependence;
				if(remainingNodes.contains(srcPDGNode) && sliceNodes.contains(dstPDGNode))
					passedParameters.add(dataDependence.getData());
				if(sliceNodes.contains(srcPDGNode) && remainingNodes.contains(dstPDGNode) &&
						!dataDependence.getData().equals(objectReference))
					nDD.add(srcPDGNode);
			}
			else if(dependence instanceof PDGControlDependence) {
				if(sliceNodes.contains(srcPDGNode) && remainingNodes.contains(dstPDGNode))
					nCD.add(srcPDGNode);
			}
		}
		Set<PDGNode> controlIndispensableNodes = new LinkedHashSet<PDGNode>();
		for(PDGNode p : nCD) {
			for(AbstractVariable usedVariable : p.usedVariables) {
				Set<PDGNode> pSliceNodes = subgraph.computeSlice(p, usedVariable);
				for(GraphNode node : pdg.nodes) {
					PDGNode q = (PDGNode)node;
					if(pSliceNodes.contains(q) || q.equals(p))
						controlIndispensableNodes.add(q);
				}
			}
		}
		Set<PDGNode> dataIndispensableNodes = new LinkedHashSet<PDGNode>();
		for(PDGNode p : nDD) {
			for(AbstractVariable definedVariable : p.definedVariables) {
				Set<PDGNode> pSliceNodes = subgraph.computeSlice(p, definedVariable);
				for(GraphNode node : pdg.nodes) {
					PDGNode q = (PDGNode)node;
					if(pSliceNodes.contains(q))
						dataIndispensableNodes.add(q);
				}
			}
		}
		this.indispensableNodes = new TreeSet<PDGNode>();
		indispensableNodes.addAll(controlIndispensableNodes);
		indispensableNodes.addAll(dataIndispensableNodes);
		for(PDGNode branchingNode : branchingNodesToBeAddedToDuplicatedNodes) {
			indispensableNodes.addAll(subgraph.computeSlice(branchingNode));
		}
		this.removableNodes = new LinkedHashSet<PDGNode>();
		for(GraphNode node : pdg.nodes) {
			PDGNode pdgNode = (PDGNode)node;
			if(!remainingNodes.contains(pdgNode) && !indispensableNodes.contains(pdgNode))
				removableNodes.add(pdgNode);
		}
		for(PDGNode node : sliceNodes) {
			IVariableBinding objectReferenceBinding = objectReference.getName().resolveBinding();
			if(node.declaresLocalVariable(objectReference) ||
					((objectReferenceBinding.isField() || objectReferenceBinding.isParameter()) &&
					node.instantiatesLocalVariable(objectReference) && node.definesLocalVariable(objectReference))) {
				removableNodes.add(node);
				indispensableNodes.remove(node);
				break;
			}
		}
	}

	private boolean isNestedInside(PDGNode nestedNode, PDGNode parentNode) {
		for(GraphEdge edge : nestedNode.incomingEdges) {
			PDGDependence dependence = (PDGDependence)edge;
			if(dependence instanceof PDGControlDependence) {
				PDGControlDependence controlDependence = (PDGControlDependence)dependence;
				PDGNode srcPDGNode = (PDGNode)controlDependence.src;
				if(srcPDGNode.equals(parentNode))
					return true;
				else
					return isNestedInside(srcPDGNode, parentNode);
			}
		}
		return false;
	}

	private Map<PDGNode, PDGNode> getInnerMostLoopNodesForBranchingNodes() {
		Map<PDGNode, PDGNode> map = new LinkedHashMap<PDGNode, PDGNode>();
		for(GraphNode node : pdg.nodes) {
			PDGNode pdgNode = (PDGNode)node;
			if(pdgNode.getASTStatement() instanceof BreakStatement || pdgNode.getASTStatement() instanceof ContinueStatement) {
				map.put(pdgNode, getInnerMostLoopNode(pdgNode));
			}
		}
		return map;
	}

	private PDGNode getInnerMostLoopNode(PDGNode node) {
		for(GraphEdge edge : node.incomingEdges) {
			PDGDependence dependence = (PDGDependence)edge;
			if(dependence instanceof PDGControlDependence) {
				PDGControlDependence controlDependence = (PDGControlDependence)dependence;
				PDGNode srcPDGNode = (PDGNode)controlDependence.src;
				CFGNode srcCFGNode = srcPDGNode.getCFGNode();
				if(srcCFGNode instanceof CFGBranchLoopNode || srcCFGNode instanceof CFGBranchDoLoopNode || srcCFGNode instanceof CFGBranchSwitchNode)
					return srcPDGNode;
				else
					return getInnerMostLoopNode(srcPDGNode);
			}
		}
		return null;
	}

	public MethodObject getMethod() {
		return method;
	}

	public IFile getIFile() {
		return iFile;
	}

	public BasicBlock getBoundaryBlock() {
		return boundaryBlock;
	}

	public PDGNode getExtractedMethodInvocationInsertionNode() {
		return ((TreeSet<PDGNode>)sliceNodes).first();
	}

	public AbstractVariable getObjectReference() {
		return objectReference;
	}

	public Set<PDGNode> getSliceNodes() {
		if(this.sliceNodes != null)
			return this.sliceNodes;
		else {
			Set<PDGNode> sliceNodes = new TreeSet<PDGNode>();
			for(PDGSliceUnion sliceUnion : sliceUnions) {
				sliceNodes.addAll(sliceUnion.getSliceNodes());
			}
			return sliceNodes;
		}
	}

	public Set<AbstractVariable> getPassedParameters() {
		return passedParameters;
	}

	public Set<PDGNode> getRemovableNodes() {
		return removableNodes;
	}

	public boolean declarationOfObjectReferenceBelongsToSliceNodes() {
		for(PDGNode node : sliceNodes) {
			if(node.declaresLocalVariable(objectReference))
				return true;
		}
		return false;
	}

	public boolean declarationOfObjectReferenceBelongsToRemovableNodes() {
		for(PDGNode node : removableNodes) {
			if(node.declaresLocalVariable(objectReference))
				return true;
		}
		return false;
	}

	private boolean sliceContainsDeclaration(AbstractVariable variableDeclaration) {
		for(PDGNode node : sliceNodes) {
			if(node.declaresLocalVariable(variableDeclaration))
				return true;
		}
		return false;
	}

	private boolean allNodeCriteriaAreDuplicated() {
		Set<PDGNode> duplicatedNodes = new LinkedHashSet<PDGNode>();
		duplicatedNodes.addAll(sliceNodes);
		duplicatedNodes.retainAll(indispensableNodes);
		for(PDGSliceUnion sliceUnion : sliceUnions) {
			for(PDGNode nodeCriterion : sliceUnion.getNodeCriteria()) {
				if(!duplicatedNodes.contains(nodeCriterion))
					return false;
			}
		}
		return true;
	}

	private boolean returnStatementIsControlDependentOnSliceNode() {
		for(GraphNode node : pdg.nodes) {
			PDGNode pdgNode = (PDGNode)node;
			if(pdgNode.getASTStatement() instanceof ReturnStatement) {
				if(isControlDependentOnSliceNode(pdgNode))
					return true;
				if(sliceNodes.contains(pdgNode))
					return true;
			}
		}
		return false;
	}

	private boolean isControlDependentOnSliceNode(PDGNode node) {
		for(GraphEdge edge : node.incomingEdges) {
			PDGDependence dependence = (PDGDependence)edge;
			if(dependence instanceof PDGControlDependence) {
				PDGControlDependence controlDependence = (PDGControlDependence)dependence;
				PDGNode srcPDGNode = (PDGNode)controlDependence.src;
				if(sliceNodes.contains(srcPDGNode))
					return true;
				else
					return isControlDependentOnSliceNode(srcPDGNode);
			}
		}
		return false;
	}

	private boolean nonDuplicatedSliceNodeAntiDependsOnNonRemovableNode() {
		Set<PDGNode> duplicatedNodes = new LinkedHashSet<PDGNode>();
		duplicatedNodes.addAll(sliceNodes);
		duplicatedNodes.retainAll(indispensableNodes);
		for(PDGNode sliceNode : sliceNodes) {
			if(!duplicatedNodes.contains(sliceNode)) {
				for(GraphEdge edge : sliceNode.incomingEdges) {
					PDGDependence dependence = (PDGDependence)edge;
					if(subgraph.edgeBelongsToBlockBasedRegion(dependence) && dependence instanceof PDGAntiDependence) {
						PDGAntiDependence antiDependence = (PDGAntiDependence)dependence;
						PDGNode srcPDGNode = (PDGNode)antiDependence.src;
						if(!removableNodes.contains(srcPDGNode))
							return true;
					}
				}
			}
		}
		return false;
	}

	private boolean duplicatedSliceNodeWithClassInstantiationHasDependenceOnRemovableNode() {
		Set<PDGNode> duplicatedNodes = new LinkedHashSet<PDGNode>();
		duplicatedNodes.addAll(sliceNodes);
		duplicatedNodes.retainAll(indispensableNodes);
		for(PDGNode duplicatedNode : duplicatedNodes) {
			if(duplicatedNode.containsClassInstanceCreation()) {
				Map<VariableDeclaration, ClassInstanceCreation> classInstantiations = duplicatedNode.getClassInstantiations();
				for(VariableDeclaration variableDeclaration : classInstantiations.keySet()) {
					for(GraphEdge edge : duplicatedNode.outgoingEdges) {
						PDGDependence dependence = (PDGDependence)edge;
						if(subgraph.edgeBelongsToBlockBasedRegion(dependence) && dependence instanceof PDGDependence) {
							PDGDependence dataDependence = (PDGDependence)dependence;
							PDGNode dstPDGNode = (PDGNode)dataDependence.dst;
							if(removableNodes.contains(dstPDGNode)) {
								if(dstPDGNode.changesStateOfReference(variableDeclaration) ||
										dstPDGNode.assignsReference(variableDeclaration) || dstPDGNode.accessesReference(variableDeclaration))
									return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	private boolean containsDuplicateNodeWithStateChangingMethodInvocation() {
		Set<PDGNode> duplicatedNodes = new LinkedHashSet<PDGNode>();
		duplicatedNodes.addAll(sliceNodes);
		duplicatedNodes.retainAll(indispensableNodes);
		for(PDGNode node : duplicatedNodes) {
			for(AbstractVariable stateChangingVariable : node.getStateChangingVariables()) {
				PlainVariable plainVariable = null;
				if(stateChangingVariable instanceof PlainVariable) {
					plainVariable = (PlainVariable)stateChangingVariable;
				}
				else if(stateChangingVariable instanceof CompositeVariable) {
					CompositeVariable compositeVariable = (CompositeVariable)stateChangingVariable;
					plainVariable = new PlainVariable(compositeVariable.getName());
				}
				if(!sliceContainsDeclaration(plainVariable))
					return true;
			}
		}
		return false;
	}

	private boolean objectSliceEqualsMethodBody() {
		int sliceSize = sliceNodes.size();
		if(sliceSize == methodSize)
			return true;
		else if(sliceSize == methodSize - 1) {
			TreeSet<GraphNode> nonIncludedInSliceMethodNodes = new TreeSet<GraphNode>(pdg.nodes);
			nonIncludedInSliceMethodNodes.removeAll(sliceNodes);
			PDGNode pdgNode = (PDGNode)nonIncludedInSliceMethodNodes.first();
			if(pdgNode instanceof PDGExitNode)
				return true;
		}
		return false;
	}

	private boolean objectSliceHasMinimumSize() {
		int sliceSize = sliceNodes.size();
		if(sliceSize == 1)
			return true;
		else if(sliceSize == 2) {
			if(sliceContainsDeclaration(objectReference))
				return true;
		}
		return false;
	}

	private boolean objectReferenceIsReturnedVariableInOriginalMethod() {
		if(pdg.getReturnedVariables().contains(objectReference))
			return true;
		return false;
	}

	private boolean complyWithUserThresholds() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		int minimumSliceSize = store.getInt(PreferenceConstants.P_MINIMUM_SLICE_SIZE);
		int maximumSliceSize = store.getInt(PreferenceConstants.P_MAXIMUM_SLICE_SIZE);
		int maximumDuplication = store.getInt(PreferenceConstants.P_MAXIMUM_DUPLICATION);
		double maximumRatioOfDuplicatedToExtracted = store.getDouble(
				PreferenceConstants.P_MAXIMUM_RATIO_OF_DUPLICATED_TO_EXTRACTED);
		
		int sliceSize = sliceNodes.size();
		int duplicatedSize = sliceSize - removableNodes.size();
		double ratioOfDuplicatedToExtracted = (double)duplicatedSize/(double)sliceSize;
		
		if(sliceSize < minimumSliceSize)
			return false;
		if(sliceSize > (methodSize - maximumSliceSize))
			return false;
		if(duplicatedSize > maximumDuplication)
			return false;
		if(ratioOfDuplicatedToExtracted > maximumRatioOfDuplicatedToExtracted)
			return false;
		return true;
	}

	public boolean satisfiesRules() {
		if(objectSliceEqualsMethodBody() || objectSliceHasMinimumSize() ||
				objectReferenceIsReturnedVariableInOriginalMethod() ||
				allNodeCriteriaAreDuplicated() || returnStatementIsControlDependentOnSliceNode() ||
				containsDuplicateNodeWithStateChangingMethodInvocation() ||
				nonDuplicatedSliceNodeAntiDependsOnNonRemovableNode() ||
				duplicatedSliceNodeWithClassInstantiationHasDependenceOnRemovableNode() ||
				!complyWithUserThresholds())
			return false;
		return true;
	}
}
