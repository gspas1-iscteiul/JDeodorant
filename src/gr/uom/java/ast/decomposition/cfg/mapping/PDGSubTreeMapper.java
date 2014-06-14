package gr.uom.java.ast.decomposition.cfg.mapping;

import gr.uom.java.ast.ASTInformationGenerator;
import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.FieldObject;
import gr.uom.java.ast.MethodInvocationObject;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.decomposition.AbstractExpression;
import gr.uom.java.ast.decomposition.AbstractStatement;
import gr.uom.java.ast.decomposition.CatchClauseObject;
import gr.uom.java.ast.decomposition.CompositeStatementObject;
import gr.uom.java.ast.decomposition.StatementObject;
import gr.uom.java.ast.decomposition.TryStatementObject;
import gr.uom.java.ast.decomposition.cfg.AbstractVariable;
import gr.uom.java.ast.decomposition.cfg.CFGBreakNode;
import gr.uom.java.ast.decomposition.cfg.CFGContinueNode;
import gr.uom.java.ast.decomposition.cfg.CFGExitNode;
import gr.uom.java.ast.decomposition.cfg.CFGNode;
import gr.uom.java.ast.decomposition.cfg.CFGThrowNode;
import gr.uom.java.ast.decomposition.cfg.CompositeVariable;
import gr.uom.java.ast.decomposition.cfg.GraphEdge;
import gr.uom.java.ast.decomposition.cfg.GraphNode;
import gr.uom.java.ast.decomposition.cfg.MethodCallAnalyzer;
import gr.uom.java.ast.decomposition.cfg.PDG;
import gr.uom.java.ast.decomposition.cfg.PDGAbstractDataDependence;
import gr.uom.java.ast.decomposition.cfg.PDGAntiDependence;
import gr.uom.java.ast.decomposition.cfg.PDGBlockNode;
import gr.uom.java.ast.decomposition.cfg.PDGControlDependence;
import gr.uom.java.ast.decomposition.cfg.PDGControlPredicateNode;
import gr.uom.java.ast.decomposition.cfg.PDGDataDependence;
import gr.uom.java.ast.decomposition.cfg.PDGDependence;
import gr.uom.java.ast.decomposition.cfg.PDGExpression;
import gr.uom.java.ast.decomposition.cfg.PDGMethodEntryNode;
import gr.uom.java.ast.decomposition.cfg.PDGNode;
import gr.uom.java.ast.decomposition.cfg.PDGOutputDependence;
import gr.uom.java.ast.decomposition.cfg.PlainVariable;
import gr.uom.java.ast.decomposition.cfg.mapping.precondition.DualExpressionPreconditionViolation;
import gr.uom.java.ast.decomposition.cfg.mapping.precondition.DualExpressionWithCommonSuperTypePreconditionViolation;
import gr.uom.java.ast.decomposition.cfg.mapping.precondition.ExpressionPreconditionViolation;
import gr.uom.java.ast.decomposition.cfg.mapping.precondition.PreconditionViolation;
import gr.uom.java.ast.decomposition.cfg.mapping.precondition.PreconditionViolationType;
import gr.uom.java.ast.decomposition.cfg.mapping.precondition.ReturnedVariablePreconditionViolation;
import gr.uom.java.ast.decomposition.cfg.mapping.precondition.StatementPreconditionViolation;
import gr.uom.java.ast.decomposition.matching.ASTNodeDifference;
import gr.uom.java.ast.decomposition.matching.ASTNodeMatcher;
import gr.uom.java.ast.decomposition.matching.BindingSignaturePair;
import gr.uom.java.ast.decomposition.matching.Difference;
import gr.uom.java.ast.decomposition.matching.DifferenceType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.VariableDeclaration;

public class PDGSubTreeMapper extends DivideAndConquerMatcher {
	private PDG pdg1;
	private PDG pdg2;
	private ICompilationUnit iCompilationUnit1;
	private ICompilationUnit iCompilationUnit2;
	private TreeSet<PDGNode> mappedNodesG1;
	private TreeSet<PDGNode> mappedNodesG2;
	private TreeSet<PDGNode> nonMappedNodesG1;
	private TreeSet<PDGNode> nonMappedNodesG2;
	private Map<VariableBindingKeyPair, ArrayList<AbstractVariable>> commonPassedParameters;
	private Map<VariableBindingKeyPair, ArrayList<AbstractVariable>> declaredLocalVariablesInMappedNodes;
	private Set<AbstractVariable> passedParametersG1;
	private Set<AbstractVariable> passedParametersG2;
	private Set<AbstractVariable> accessedLocalFieldsG1;
	private Set<AbstractVariable> accessedLocalFieldsG2;
	private Set<MethodInvocationObject> accessedLocalMethodsG1;
	private Set<MethodInvocationObject> accessedLocalMethodsG2;
	private Set<AbstractVariable> declaredVariablesInMappedNodesUsedByNonMappedNodesG1;
	private Set<AbstractVariable> declaredVariablesInMappedNodesUsedByNonMappedNodesG2;
	private List<PreconditionViolation> preconditionViolations;
	private Set<PlainVariable> variablesToBeReturnedG1;
	private Set<PlainVariable> variablesToBeReturnedG2;
	private TreeSet<PDGNode> nonMappedPDGNodesG1MovableBefore;
	private TreeSet<PDGNode> nonMappedPDGNodesG1MovableAfter;
	private TreeSet<PDGNode> nonMappedPDGNodesG1MovableBeforeAndAfter;
	private TreeSet<PDGNode> nonMappedPDGNodesG2MovableBefore;
	private TreeSet<PDGNode> nonMappedPDGNodesG2MovableAfter;
	private TreeSet<PDGNode> nonMappedPDGNodesG2MovableBeforeAndAfter;
	
	public PDGSubTreeMapper(PDG pdg1, PDG pdg2,
			ICompilationUnit iCompilationUnit1, ICompilationUnit iCompilationUnit2,
			ControlDependenceTreeNode controlDependenceSubTreePDG1,
			ControlDependenceTreeNode controlDependenceSubTreePDG2,
			boolean fullTreeMatch, IProgressMonitor monitor) {
		super(pdg1, pdg2, iCompilationUnit1, iCompilationUnit2, controlDependenceSubTreePDG1, controlDependenceSubTreePDG2, fullTreeMatch, monitor);
		this.pdg1 = pdg1;
		this.pdg2 = pdg2;
		this.iCompilationUnit1 = iCompilationUnit1;
		this.iCompilationUnit2 = iCompilationUnit2;
		this.nonMappedNodesG1 = new TreeSet<PDGNode>();
		this.nonMappedNodesG2 = new TreeSet<PDGNode>();
		this.commonPassedParameters = new LinkedHashMap<VariableBindingKeyPair, ArrayList<AbstractVariable>>();
		this.declaredLocalVariablesInMappedNodes = new LinkedHashMap<VariableBindingKeyPair, ArrayList<AbstractVariable>>();
		this.passedParametersG1 = new LinkedHashSet<AbstractVariable>();
		this.passedParametersG2 = new LinkedHashSet<AbstractVariable>();
		this.accessedLocalFieldsG1 = new LinkedHashSet<AbstractVariable>();
		this.accessedLocalFieldsG2 = new LinkedHashSet<AbstractVariable>();
		this.accessedLocalMethodsG1 = new LinkedHashSet<MethodInvocationObject>();
		this.accessedLocalMethodsG2 = new LinkedHashSet<MethodInvocationObject>();
		this.declaredVariablesInMappedNodesUsedByNonMappedNodesG1 = new LinkedHashSet<AbstractVariable>();
		this.declaredVariablesInMappedNodesUsedByNonMappedNodesG2 = new LinkedHashSet<AbstractVariable>();
		this.preconditionViolations = new ArrayList<PreconditionViolation>();
		this.nonMappedPDGNodesG1MovableBefore = new TreeSet<PDGNode>();
		this.nonMappedPDGNodesG1MovableAfter = new TreeSet<PDGNode>();
		this.nonMappedPDGNodesG1MovableBeforeAndAfter = new TreeSet<PDGNode>();
		this.nonMappedPDGNodesG2MovableBefore = new TreeSet<PDGNode>();
		this.nonMappedPDGNodesG2MovableAfter = new TreeSet<PDGNode>();
		this.nonMappedPDGNodesG2MovableBeforeAndAfter = new TreeSet<PDGNode>();
		//creates CloneStructureRoot
		matchBasedOnControlDependenceTreeStructure();
		if(getMaximumStateWithMinimumDifferences() != null) {
			this.mappedNodesG1 = getMaximumStateWithMinimumDifferences().getMappedNodesG1();
			this.mappedNodesG2 = getMaximumStateWithMinimumDifferences().getMappedNodesG2();
			findNonMappedNodes(pdg1, getAllNodesInSubTreePDG1(), mappedNodesG1, nonMappedNodesG1);
			findNonMappedNodes(pdg2, getAllNodesInSubTreePDG2(), mappedNodesG2, nonMappedNodesG2);
			Set<PDGNode> additionallyMatchedNodesG1 = new LinkedHashSet<PDGNode>();
			for(PDGNode nodeG1 : nonMappedNodesG1) {
				boolean advancedMatch = getCloneStructureRoot().isGapNodeG1InAdditionalMatches(nodeG1);
				if(advancedMatch) {
					additionallyMatchedNodesG1.add(nodeG1);
				}
				PDGNodeGap nodeGap = new PDGNodeGap(nodeG1, null, advancedMatch);
				CloneStructureNode node = new CloneStructureNode(nodeGap);
				PDGBlockNode tryNode = pdg1.isDirectlyNestedWithinBlockNode(nodeG1);
				if(tryNode != null) {
					CloneStructureNode cloneStructureTry = getCloneStructureRoot().findNodeG1(tryNode);
					if(cloneStructureTry != null) {
						node.setParent(cloneStructureTry);
					}
				}
				else {
					getCloneStructureRoot().addGapChild(node);
				}
			}
			nonMappedNodesG1.removeAll(additionallyMatchedNodesG1);
			Set<PDGNode> additionallyMatchedNodesG2 = new LinkedHashSet<PDGNode>();
			for(PDGNode nodeG2 : nonMappedNodesG2) {
				boolean advancedMatch = getCloneStructureRoot().isGapNodeG2InAdditionalMatches(nodeG2);
				if(advancedMatch) {
					additionallyMatchedNodesG2.add(nodeG2);
				}
				PDGNodeGap nodeGap = new PDGNodeGap(null, nodeG2, advancedMatch);
				CloneStructureNode node = new CloneStructureNode(nodeGap);
				PDGBlockNode tryNode = pdg2.isDirectlyNestedWithinBlockNode(nodeG2);
				if(tryNode != null) {
					CloneStructureNode cloneStructureTry = getCloneStructureRoot().findNodeG2(tryNode);
					if(cloneStructureTry != null) {
						node.setParent(cloneStructureTry);
					}
				}
				else {
					getCloneStructureRoot().addGapChild(node);
				}
			}
			nonMappedNodesG2.removeAll(additionallyMatchedNodesG2);
			findDeclaredVariablesInMappedNodesUsedByNonMappedNodes(pdg1, mappedNodesG1, declaredVariablesInMappedNodesUsedByNonMappedNodesG1);
			findDeclaredVariablesInMappedNodesUsedByNonMappedNodes(pdg2, mappedNodesG2, declaredVariablesInMappedNodesUsedByNonMappedNodesG2);
			findPassedParameters();
			findLocallyAccessedFields(pdg1, mappedNodesG1, accessedLocalFieldsG1, accessedLocalMethodsG1);
			findLocallyAccessedFields(pdg2, mappedNodesG2, accessedLocalFieldsG2, accessedLocalMethodsG2);
			this.variablesToBeReturnedG1 = variablesToBeReturned(pdg1, getRemovableNodesG1());
			this.variablesToBeReturnedG2 = variablesToBeReturned(pdg2, getRemovableNodesG2());
			checkCloneStructureNodeForPreconditions(getCloneStructureRoot());
			processNonMappedNodesMovableBeforeAndAfter();
			checkPreconditionsAboutReturnedVariables();
		}
	}

	private void findNonMappedNodes(PDG pdg, TreeSet<PDGNode> allNodes, Set<PDGNode> mappedNodes, Set<PDGNode> nonMappedNodes) {
		PDGNode first = allNodes.first();
		PDGNode last = allNodes.last();
		Iterator<GraphNode> iterator = pdg.getNodeIterator();
		while(iterator.hasNext()) {
			PDGNode pdgNode = (PDGNode)iterator.next();
			if(pdgNode.getId() >= first.getId() && pdgNode.getId() <= last.getId()) {
				if(!mappedNodes.contains(pdgNode)) {
					nonMappedNodes.add(pdgNode);
				}
			}
		}
	}

	private void findDeclaredVariablesInMappedNodesUsedByNonMappedNodes(PDG pdg, Set<PDGNode> mappedNodes, Set<AbstractVariable> variables) {
		for(PDGNode mappedNode : mappedNodes) {
			for(Iterator<AbstractVariable> declaredVariableIterator = mappedNode.getDeclaredVariableIterator(); declaredVariableIterator.hasNext();) {
				AbstractVariable declaredVariable = declaredVariableIterator.next();
				for(GraphNode node : pdg.getNodes()) {
					PDGNode pdgNode = (PDGNode)node;
					if(!mappedNodes.contains(pdgNode)) {
						if(pdgNode.usesLocalVariable(declaredVariable) || pdgNode.definesLocalVariable(declaredVariable)) {
							variables.add(declaredVariable);
							break;
						}
					}
				}
			}
		}
	}

	private void findPassedParameters() {
		Set<AbstractVariable> passedParametersG1 = extractPassedParameters(pdg1, mappedNodesG1);
		Set<AbstractVariable> passedParametersG2 = extractPassedParameters(pdg2, mappedNodesG2);
		Set<AbstractVariable> parametersToBeRemovedG1 = new LinkedHashSet<AbstractVariable>();
		Set<AbstractVariable> parametersToBeRemovedG2 = new LinkedHashSet<AbstractVariable>();
		for(PDGNodeMapping nodeMapping : getMaximumStateWithMinimumDifferences().getNodeMappings()) {
			PDGNode nodeG1 = nodeMapping.getNodeG1();
			PDGNode nodeG2 = nodeMapping.getNodeG2();
			List<AbstractVariable> nonAnonymousDeclaredVariablesG1 = new ArrayList<AbstractVariable>();
			Iterator<AbstractVariable> declaredVariableIteratorG1 = nodeG1.getDeclaredVariableIterator();
			while(declaredVariableIteratorG1.hasNext()) {
				AbstractVariable declaredVariableG1 = declaredVariableIteratorG1.next();
				String key1 = declaredVariableG1.getVariableBindingKey();
				String declaringType1 = key1.substring(0, key1.indexOf(";"));
				if(!declaringType1.contains("$")) {
					nonAnonymousDeclaredVariablesG1.add(declaredVariableG1);
				}
			}
			List<AbstractVariable> nonAnonymousDeclaredVariablesG2 = new ArrayList<AbstractVariable>();
			Iterator<AbstractVariable> declaredVariableIteratorG2 = nodeG2.getDeclaredVariableIterator();
			while(declaredVariableIteratorG2.hasNext()) {
				AbstractVariable declaredVariableG2 = declaredVariableIteratorG2.next();
				String key2 = declaredVariableG2.getVariableBindingKey();
				String declaringType2 = key2.substring(0, key2.indexOf(";"));
				if(!declaringType2.contains("$")) {
					nonAnonymousDeclaredVariablesG2.add(declaredVariableG2);
				}
			}
			int min = Math.min(nonAnonymousDeclaredVariablesG1.size(), nonAnonymousDeclaredVariablesG2.size());
			for(int i=0; i<min; i++) {
				AbstractVariable declaredVariableG1 = nonAnonymousDeclaredVariablesG1.get(i);
				AbstractVariable declaredVariableG2 = nonAnonymousDeclaredVariablesG2.get(i);
				ArrayList<AbstractVariable> declaredVariables = new ArrayList<AbstractVariable>();
				declaredVariables.add(declaredVariableG1);
				declaredVariables.add(declaredVariableG2);
				VariableBindingKeyPair keyPair = new VariableBindingKeyPair(declaredVariableG1.getVariableBindingKey(),
						declaredVariableG2.getVariableBindingKey());
				declaredLocalVariablesInMappedNodes.put(keyPair, declaredVariables);
			}
			Set<AbstractVariable> dataDependences1 = nodeG1.incomingDataDependencesFromNodesDeclaringVariables();
			Set<AbstractVariable> dataDependences2 = nodeG2.incomingDataDependencesFromNodesDeclaringVariables();
			dataDependences1.retainAll(passedParametersG1);
			dataDependences2.retainAll(passedParametersG2);
			if(dataDependences1.size() == dataDependences2.size()) {
				List<AbstractVariable> variables1 = new ArrayList<AbstractVariable>(dataDependences1);
				List<AbstractVariable> variables2 = new ArrayList<AbstractVariable>(dataDependences2);
				for(int i=0; i<variables1.size(); i++) {
					AbstractVariable variable1 = variables1.get(i);
					AbstractVariable variable2 = variables2.get(i);
					if(passedParametersG1.contains(variable1) && passedParametersG2.contains(variable2)) {
						ArrayList<AbstractVariable> variableDeclarations = new ArrayList<AbstractVariable>();
						variableDeclarations.add(variable1);
						variableDeclarations.add(variable2);
						VariableBindingKeyPair keyPair = new VariableBindingKeyPair(variable1.getVariableBindingKey(),
								variable2.getVariableBindingKey());
						commonPassedParameters.put(keyPair, variableDeclarations);
						parametersToBeRemovedG1.add(variable1);
						parametersToBeRemovedG2.add(variable2);
					}
				}
			}
		}
		passedParametersG1.removeAll(parametersToBeRemovedG1);
		passedParametersG2.removeAll(parametersToBeRemovedG2);
		this.passedParametersG1.addAll(passedParametersG1);
		this.passedParametersG2.addAll(passedParametersG2);
	}

	private Set<AbstractVariable> extractPassedParameters(PDG pdg, Set<PDGNode> mappedNodes) {
		Set<AbstractVariable> passedParameters = new LinkedHashSet<AbstractVariable>();
		for(GraphEdge edge : pdg.getEdges()) {
			PDGDependence dependence = (PDGDependence)edge;
			PDGNode srcPDGNode = (PDGNode)dependence.getSrc();
			PDGNode dstPDGNode = (PDGNode)dependence.getDst();
			if(dependence instanceof PDGDataDependence) {
				PDGDataDependence dataDependence = (PDGDataDependence)dependence;
				if(!mappedNodes.contains(srcPDGNode) && mappedNodes.contains(dstPDGNode)) {
					passedParameters.add(dataDependence.getData());
				}
			}
		}
		return passedParameters;
	}

	private void findLocallyAccessedFields(PDG pdg, Set<PDGNode> mappedNodes, Set<AbstractVariable> accessedFields,
			Set<MethodInvocationObject> accessedMethods) {
		Set<PlainVariable> usedLocalFields = new LinkedHashSet<PlainVariable>();
		Set<MethodInvocationObject> accessedLocalMethods = new LinkedHashSet<MethodInvocationObject>();
		for(PDGNode pdgNode : mappedNodes) {
			AbstractStatement abstractStatement = pdgNode.getStatement();
			if(abstractStatement instanceof StatementObject) {
				StatementObject statement = (StatementObject)abstractStatement;
				usedLocalFields.addAll(statement.getUsedFieldsThroughThisReference());
				accessedLocalMethods.addAll(statement.getInvokedMethodsThroughThisReference());
				accessedLocalMethods.addAll(statement.getInvokedStaticMethods());
			}
			else if(abstractStatement instanceof CompositeStatementObject) {
				CompositeStatementObject composite = (CompositeStatementObject)abstractStatement;
				usedLocalFields.addAll(composite.getUsedFieldsThroughThisReferenceInExpressions());
				accessedLocalMethods.addAll(composite.getInvokedMethodsThroughThisReferenceInExpressions());
				accessedLocalMethods.addAll(composite.getInvokedStaticMethodsInExpressions());
				if(composite instanceof TryStatementObject) {
					TryStatementObject tryStatement = (TryStatementObject)composite;
					List<CatchClauseObject> catchClauses = tryStatement.getCatchClauses();
					for(CatchClauseObject catchClause : catchClauses) {
						usedLocalFields.addAll(catchClause.getBody().getUsedFieldsThroughThisReference());
						accessedLocalMethods.addAll(catchClause.getBody().getInvokedMethodsThroughThisReference());
						accessedLocalMethods.addAll(catchClause.getBody().getInvokedStaticMethods());
					}
					if(tryStatement.getFinallyClause() != null) {
						usedLocalFields.addAll(tryStatement.getFinallyClause().getUsedFieldsThroughThisReference());
						accessedLocalMethods.addAll(tryStatement.getFinallyClause().getInvokedMethodsThroughThisReference());
						accessedLocalMethods.addAll(tryStatement.getFinallyClause().getInvokedStaticMethods());
					}
				}
			}
		}
		ITypeBinding declaringClassTypeBinding = pdg.getMethod().getMethodDeclaration().resolveBinding().getDeclaringClass();
		Set<VariableDeclaration> fieldsAccessedInMethod = pdg.getFieldsAccessedInMethod();
		for(PlainVariable variable : usedLocalFields) {
			for(VariableDeclaration fieldDeclaration : fieldsAccessedInMethod) {
				if(variable.getVariableBindingKey().equals(fieldDeclaration.resolveBinding().getKey()) &&
						fieldDeclaration.resolveBinding().getDeclaringClass().isEqualTo(declaringClassTypeBinding)) {
					accessedFields.add(variable);
					break;
				}
			}
		}
		for(MethodInvocationObject invocation : accessedLocalMethods) {
			if(invocation.getMethodInvocation().resolveMethodBinding().getDeclaringClass().isEqualTo(declaringClassTypeBinding)) {
				//exclude recursive method calls
				if(!pdg.getMethod().getMethodDeclaration().resolveBinding().isEqualTo(invocation.getMethodInvocation().resolveMethodBinding())) {
					accessedMethods.add(invocation);
					getAdditionalLocallyAccessedFieldsAndMethods(invocation, accessedFields, accessedMethods);
				}
			}
		}
	}
	
	private void getAdditionalLocallyAccessedFieldsAndMethods(MethodInvocationObject methodCall,
			Set<AbstractVariable> accessedFields, Set<MethodInvocationObject> accessedMethods) {
		SystemObject system = ASTReader.getSystemObject();
		MethodObject calledMethod = system.getMethod(methodCall);
		if(calledMethod != null) {
			ClassObject calledClass = system.getClassObject(calledMethod.getClassName());
			Set<PlainVariable> usedLocalFields = new LinkedHashSet<PlainVariable>();
			Set<MethodInvocationObject> accessedLocalMethods = new LinkedHashSet<MethodInvocationObject>();
			usedLocalFields.addAll(calledMethod.getUsedFieldsThroughThisReference());
			accessedLocalMethods.addAll(calledMethod.getInvokedMethodsThroughThisReference());
			accessedLocalMethods.addAll(calledMethod.getInvokedStaticMethods());
			ITypeBinding declaringClassTypeBinding = calledMethod.getMethodDeclaration().resolveBinding().getDeclaringClass();
			Set<FieldObject> fieldsAccessedInMethod = calledClass.getFieldsAccessedInsideMethod(calledMethod);
			for(PlainVariable variable : usedLocalFields) {
				for(FieldObject fieldDeclaration : fieldsAccessedInMethod) {
					IVariableBinding fieldBinding = fieldDeclaration.getVariableDeclaration().resolveBinding();
					if(variable.getVariableBindingKey().equals(fieldBinding.getKey()) &&
							fieldBinding.getDeclaringClass().isEqualTo(declaringClassTypeBinding)) {
						accessedFields.add(variable);
						break;
					}
				}
			}
			for(MethodInvocationObject invocation : accessedLocalMethods) {
				if(invocation.getMethodInvocation().resolveMethodBinding().getDeclaringClass().isEqualTo(declaringClassTypeBinding)) {
					if(!accessedMethods.contains(invocation)) {
						accessedMethods.add(invocation);
						getAdditionalLocallyAccessedFieldsAndMethods(invocation, accessedFields, accessedMethods);
					}
				}
			}
		}
	}

	protected Set<PDGNode> getNodesInRegion1(PDG pdg, PDGNode controlPredicate, Set<PDGNode> controlPredicateNodesInCurrentLevel,
			Set<PDGNode> controlPredicateNodesInNextLevel, ControlDependenceTreeNode controlDependenceTreeRoot) {
		return getNodesInRegion(pdg, controlPredicate, controlPredicateNodesInCurrentLevel, controlPredicateNodesInNextLevel, controlDependenceTreeRoot);
	}

	protected Set<PDGNode> getNodesInRegion2(PDG pdg, PDGNode controlPredicate, Set<PDGNode> controlPredicateNodesInCurrentLevel,
			Set<PDGNode> controlPredicateNodesInNextLevel, ControlDependenceTreeNode controlDependenceTreeRoot) {
		return getNodesInRegion(pdg, controlPredicate, controlPredicateNodesInCurrentLevel, controlPredicateNodesInNextLevel, controlDependenceTreeRoot);
	}

	protected Set<PDGNode> getElseNodesOfSymmetricalIfStatement1(PDG pdg, PDGNode controlPredicate, Set<PDGNode> controlPredicateNodesInCurrentLevel,
			Set<PDGNode> controlPredicateNodesInNextLevel) {
		return getElseNodesOfSymmetricalIfStatement(pdg, controlPredicate, controlPredicateNodesInCurrentLevel, controlPredicateNodesInNextLevel);
	}

	protected Set<PDGNode> getElseNodesOfSymmetricalIfStatement2(PDG pdg, PDGNode controlPredicate, Set<PDGNode> controlPredicateNodesInCurrentLevel,
			Set<PDGNode> controlPredicateNodesInNextLevel) {
		return getElseNodesOfSymmetricalIfStatement(pdg, controlPredicate, controlPredicateNodesInCurrentLevel, controlPredicateNodesInNextLevel);
	}

	protected List<ControlDependenceTreeNode> getIfParentChildren1(ControlDependenceTreeNode cdtNode) {
		return getIfParentChildren(cdtNode);
	}

	protected List<ControlDependenceTreeNode> getIfParentChildren2(ControlDependenceTreeNode cdtNode) {
		return getIfParentChildren(cdtNode);
	}

	private Set<PDGNode> getNodesInRegion(PDG pdg, PDGNode controlPredicate, Set<PDGNode> controlPredicateNodesInCurrentLevel,
			Set<PDGNode> controlPredicateNodesInNextLevel, ControlDependenceTreeNode controlDependenceTreeRoot) {
		Set<PDGNode> nodesInRegion = new TreeSet<PDGNode>();
		if(!(controlPredicate instanceof PDGMethodEntryNode) &&
				!controlPredicate.equals(controlDependenceTreeRoot.getNode()))
			nodesInRegion.add(controlPredicate);
		if(controlPredicate instanceof PDGBlockNode) {
			Set<PDGNode> nestedNodesWithinTryNode = pdg.getNestedNodesWithinBlockNode((PDGBlockNode)controlPredicate);
			for(PDGNode nestedNode : nestedNodesWithinTryNode) {
				if(!controlPredicateNodesInNextLevel.contains(nestedNode) && !controlPredicateNodesInCurrentLevel.contains(nestedNode)) {
					if(!(nestedNode instanceof PDGControlPredicateNode))
						nodesInRegion.add(nestedNode);
				}
			}
		}
		else {
			Iterator<GraphEdge> edgeIterator = controlPredicate.getOutgoingDependenceIterator();
			while(edgeIterator.hasNext()) {
				PDGDependence dependence = (PDGDependence)edgeIterator.next();
				if(dependence instanceof PDGControlDependence) {
					PDGNode pdgNode = (PDGNode)dependence.getDst();
					PDGBlockNode tryNode = pdg.isDirectlyNestedWithinBlockNode(pdgNode);
					if(!controlPredicateNodesInNextLevel.contains(pdgNode) && !controlPredicateNodesInCurrentLevel.contains(pdgNode) && tryNode == null) {
						if(!(pdgNode instanceof PDGControlPredicateNode))
							nodesInRegion.add(pdgNode);
					}
				}
			}
		}
		return nodesInRegion;
	}
	
	private Set<PDGNode> getElseNodesOfSymmetricalIfStatement(PDG pdg, PDGNode controlPredicate, Set<PDGNode> controlPredicateNodesInCurrentLevel,
			Set<PDGNode> controlPredicateNodesInNextLevel) {
		Set<PDGNode> nodesInRegion = new TreeSet<PDGNode>();
		Iterator<GraphEdge> edgeIterator = controlPredicate.getOutgoingDependenceIterator();
		while(edgeIterator.hasNext()) {
			PDGDependence dependence = (PDGDependence)edgeIterator.next();
			if(dependence instanceof PDGControlDependence) {
				PDGControlDependence pdgControlDependence = (PDGControlDependence)dependence;
				if(pdgControlDependence.isFalseControlDependence()) {
					PDGNode pdgNode = (PDGNode)dependence.getDst();
					PDGBlockNode tryNode = pdg.isDirectlyNestedWithinBlockNode(pdgNode);
					if(!controlPredicateNodesInNextLevel.contains(pdgNode) && !controlPredicateNodesInCurrentLevel.contains(pdgNode) && tryNode == null) {
						if(!(pdgNode instanceof PDGControlPredicateNode))
							nodesInRegion.add(pdgNode);
					}
				}
			}
		}
		return nodesInRegion;
	}

	private List<ControlDependenceTreeNode> getIfParentChildren(ControlDependenceTreeNode cdtNode) {
		List<ControlDependenceTreeNode> children = new ArrayList<ControlDependenceTreeNode>();
		if(cdtNode != null && cdtNode.isElseNode()) {
			ControlDependenceTreeNode ifParent = cdtNode.getIfParent();
			if(ifParent != null) {
				children.addAll(ifParent.getChildren());
			}
		}
		return children;
	}

	public PDG getPDG1() {
		return pdg1;
	}

	public PDG getPDG2() {
		return pdg2;
	}

	public String getMethodName1() {
		return pdg1.getMethod().getName();
	}

	public String getMethodName2() {
		return pdg2.getMethod().getName();
	}

	public TreeSet<PDGNode> getRemovableNodesG1() {
		return mappedNodesG1;
	}

	public TreeSet<PDGNode> getRemovableNodesG2() {
		return mappedNodesG2;
	}

	public TreeSet<PDGNode> getRemainingNodesG1() {
		return nonMappedNodesG1;
	}

	public TreeSet<PDGNode> getRemainingNodesG2() {
		return nonMappedNodesG2;
	}

	public TreeSet<PDGNode> getNonMappedPDGNodesG1MovableBefore() {
		return nonMappedPDGNodesG1MovableBefore;
	}

	public TreeSet<PDGNode> getNonMappedPDGNodesG1MovableAfter() {
		return nonMappedPDGNodesG1MovableAfter;
	}

	public TreeSet<PDGNode> getNonMappedPDGNodesG2MovableBefore() {
		return nonMappedPDGNodesG2MovableBefore;
	}

	public TreeSet<PDGNode> getNonMappedPDGNodesG2MovableAfter() {
		return nonMappedPDGNodesG2MovableAfter;
	}

	public Set<VariableDeclaration> getDeclaredVariablesInMappedNodesUsedByNonMappedNodesG1() {
		Set<VariableDeclaration> declaredVariablesG1 = new LinkedHashSet<VariableDeclaration>();
		Set<VariableDeclaration> variableDeclarationsInMethod1 = pdg1.getVariableDeclarationsInMethod();
		for(AbstractVariable variable1 : this.declaredVariablesInMappedNodesUsedByNonMappedNodesG1) {
			for(VariableDeclaration variableDeclaration : variableDeclarationsInMethod1) {
				if(variableDeclaration.resolveBinding().getKey().equals(variable1.getVariableBindingKey())) {
					declaredVariablesG1.add(variableDeclaration);
					break;
				}
			}
		}
		return declaredVariablesG1;
	}

	public Set<VariableDeclaration> getDeclaredVariablesInMappedNodesUsedByNonMappedNodesG2() {
		Set<VariableDeclaration> declaredVariablesG2 = new LinkedHashSet<VariableDeclaration>();
		Set<VariableDeclaration> variableDeclarationsInMethod2 = pdg2.getVariableDeclarationsInMethod();
		for(AbstractVariable variable2 : this.declaredVariablesInMappedNodesUsedByNonMappedNodesG2) {
			for(VariableDeclaration variableDeclaration : variableDeclarationsInMethod2) {
				if(variableDeclaration.resolveBinding().getKey().equals(variable2.getVariableBindingKey())) {
					declaredVariablesG2.add(variableDeclaration);
					break;
				}
			}
		}
		return declaredVariablesG2;
	}

	public Set<AbstractVariable> getAccessedLocalFieldsG1() {
		return accessedLocalFieldsG1;
	}

	public Set<AbstractVariable> getAccessedLocalFieldsG2() {
		return accessedLocalFieldsG2;
	}

	public Set<MethodInvocationObject> getAccessedLocalMethodsG1() {
		return accessedLocalMethodsG1;
	}

	public Set<MethodInvocationObject> getAccessedLocalMethodsG2() {
		return accessedLocalMethodsG2;
	}

	public Map<VariableBindingKeyPair, ArrayList<VariableDeclaration>> getDeclaredLocalVariablesInMappedNodes() {
		Map<VariableBindingKeyPair, ArrayList<VariableDeclaration>> declaredVariables = new LinkedHashMap<VariableBindingKeyPair, ArrayList<VariableDeclaration>>();
		Set<VariableDeclaration> variableDeclarationsAndAccessedFieldsInMethod1 = pdg1.getVariableDeclarationsAndAccessedFieldsInMethod();
		Set<VariableDeclaration> variableDeclarationsAndAccessedFieldsInMethod2 = pdg2.getVariableDeclarationsAndAccessedFieldsInMethod();
		for(VariableBindingKeyPair key : this.declaredLocalVariablesInMappedNodes.keySet()) {
			ArrayList<AbstractVariable> value = this.declaredLocalVariablesInMappedNodes.get(key);
			AbstractVariable variableDeclaration1 = value.get(0);
			AbstractVariable variableDeclaration2 = value.get(1);
			ArrayList<VariableDeclaration> variableDeclarations = new ArrayList<VariableDeclaration>();
			for(VariableDeclaration variableDeclaration : variableDeclarationsAndAccessedFieldsInMethod1) {
				if(variableDeclaration.resolveBinding().getKey().equals(variableDeclaration1.getVariableBindingKey())) {
					variableDeclarations.add(variableDeclaration);
					break;
				}
			}
			for(VariableDeclaration variableDeclaration : variableDeclarationsAndAccessedFieldsInMethod2) {
				if(variableDeclaration.resolveBinding().getKey().equals(variableDeclaration2.getVariableBindingKey())) {
					variableDeclarations.add(variableDeclaration);
					break;
				}
			}
			declaredVariables.put(key, variableDeclarations);
		}
		return declaredVariables;
	}

	public Map<VariableBindingKeyPair, ArrayList<VariableDeclaration>> getCommonPassedParameters() {
		Map<VariableBindingKeyPair, ArrayList<VariableDeclaration>> commonPassedParameters = new LinkedHashMap<VariableBindingKeyPair, ArrayList<VariableDeclaration>>();
		Set<VariableDeclaration> variableDeclarationsAndAccessedFieldsInMethod1 = pdg1.getVariableDeclarationsAndAccessedFieldsInMethod();
		Set<VariableDeclaration> variableDeclarationsAndAccessedFieldsInMethod2 = pdg2.getVariableDeclarationsAndAccessedFieldsInMethod();
		for(VariableBindingKeyPair key : this.commonPassedParameters.keySet()) {
			ArrayList<AbstractVariable> value = this.commonPassedParameters.get(key);
			AbstractVariable variableDeclaration1 = value.get(0);
			AbstractVariable variableDeclaration2 = value.get(1);
			ArrayList<VariableDeclaration> variableDeclarations = new ArrayList<VariableDeclaration>();
			for(VariableDeclaration variableDeclaration : variableDeclarationsAndAccessedFieldsInMethod1) {
				if(variableDeclaration.resolveBinding().getKey().equals(variableDeclaration1.getVariableBindingKey())) {
					variableDeclarations.add(variableDeclaration);
					break;
				}
			}
			for(VariableDeclaration variableDeclaration : variableDeclarationsAndAccessedFieldsInMethod2) {
				if(variableDeclaration.resolveBinding().getKey().equals(variableDeclaration2.getVariableBindingKey())) {
					variableDeclarations.add(variableDeclaration);
					break;
				}
			}
			commonPassedParameters.put(key, variableDeclarations);
		}
		return commonPassedParameters;
	}

	public List<ASTNodeDifference> getNodeDifferences() {
		return getMaximumStateWithMinimumDifferences().getNodeDifferences();
	}

	public List<ASTNodeDifference> getNonOverlappingNodeDifferences() {
		return getMaximumStateWithMinimumDifferences().getNonOverlappingNodeDifferences();
	}

	public Set<BindingSignaturePair> getRenamedVariables() {
		List<BindingSignaturePair> variableNameMismatches = new ArrayList<BindingSignaturePair>();
		List<Difference> variableNameMismatchDifferences = new ArrayList<Difference>();
		for(ASTNodeDifference nodeDifference : getNodeDifferences()) {
			List<Difference> diffs = nodeDifference.getDifferences();
			for(Difference diff : diffs) {
				if(diff.getType().equals(DifferenceType.VARIABLE_NAME_MISMATCH)) {
					Expression expression1 = nodeDifference.getExpression1().getExpression();
					Expression expression2 = nodeDifference.getExpression2().getExpression();
					if(expression1 instanceof SimpleName && expression2 instanceof SimpleName) {
						SimpleName simpleName1 = (SimpleName)expression1;
						SimpleName simpleName2 = (SimpleName)expression2;
						IBinding binding1 = simpleName1.resolveBinding();
						IBinding binding2 = simpleName2.resolveBinding();
						if(binding1.getKind() == IBinding.VARIABLE && binding2.getKind() == IBinding.VARIABLE) {
							IVariableBinding variableBinding1 = (IVariableBinding)binding1;
							IVariableBinding variableBinding2 = (IVariableBinding)binding2;
							IMethodBinding declaringMethod1 = variableBinding1.getDeclaringMethod();
							IMethodBinding declaringMethod2 = variableBinding2.getDeclaringMethod();
							IMethodBinding  method1 = pdg1.getMethod().getMethodDeclaration().resolveBinding();
							IMethodBinding  method2 = pdg2.getMethod().getMethodDeclaration().resolveBinding();
							if(declaringMethod1 != null && declaringMethod1.isEqualTo(method1) &&
									declaringMethod2 != null && declaringMethod2.isEqualTo(method2)) {
								variableNameMismatches.add(nodeDifference.getBindingSignaturePair());
								variableNameMismatchDifferences.add(diff);
							}
						}
					}
				}
				if(diff.getType().equals(DifferenceType.TYPE_COMPATIBLE_REPLACEMENT)) {
					Expression expression1 = nodeDifference.getExpression1().getExpression();
					Expression expression2 = nodeDifference.getExpression2().getExpression();
					if(expression1 instanceof SimpleName && !(expression2 instanceof SimpleName)) {
						SimpleName simpleName1 = (SimpleName)expression1;
						IBinding binding1 = simpleName1.resolveBinding();
						if(binding1.getKind() == IBinding.VARIABLE) {
							IVariableBinding variableBinding1 = (IVariableBinding)binding1;
							IMethodBinding declaringMethod1 = variableBinding1.getDeclaringMethod();
							IMethodBinding  method1 = pdg1.getMethod().getMethodDeclaration().resolveBinding();
							if(declaringMethod1 != null && declaringMethod1.isEqualTo(method1)) {
								variableNameMismatches.add(nodeDifference.getBindingSignaturePair());
								variableNameMismatchDifferences.add(diff);
							}
						}
					}
					else if(!(expression1 instanceof SimpleName) && expression2 instanceof SimpleName) {
						SimpleName simpleName2 = (SimpleName)expression2;
						IBinding binding2 = simpleName2.resolveBinding();
						if(binding2.getKind() == IBinding.VARIABLE) {
							IVariableBinding variableBinding2 = (IVariableBinding)binding2;
							IMethodBinding declaringMethod2 = variableBinding2.getDeclaringMethod();
							IMethodBinding  method2 = pdg2.getMethod().getMethodDeclaration().resolveBinding();
							if(declaringMethod2 != null && declaringMethod2.isEqualTo(method2)) {
								variableNameMismatches.add(nodeDifference.getBindingSignaturePair());
								variableNameMismatchDifferences.add(diff);
							}
						}
					}
				}
			}
		}
		Set<BindingSignaturePair> renamedVariables = new LinkedHashSet<BindingSignaturePair>();
		Set<BindingSignaturePair> swappedVariables = new LinkedHashSet<BindingSignaturePair>();
		for(int i=0; i<variableNameMismatches.size(); i++) {
			BindingSignaturePair signaturePairI = variableNameMismatches.get(i);
			if(!renamedVariables.contains(signaturePairI)) {
				boolean isRenamed = true;
				boolean isSwapped = true;
				int renameCount = 0;
				int swapCount = 0;
				for(int j=0; j<variableNameMismatches.size(); j++) {
					BindingSignaturePair signaturePairJ = variableNameMismatches.get(j);
					if(signaturePairI.getSignature1().equals(signaturePairJ.getSignature1())) {
						if(signaturePairI.getSignature2().equals(signaturePairJ.getSignature2())) {
							renameCount++;
						}
						else {
							isRenamed = false;
							break;
						}
					}
					else if(signaturePairI.getSignature2().equals(signaturePairJ.getSignature2())) {
						if(signaturePairI.getSignature1().equals(signaturePairJ.getSignature1())) {
							renameCount++;
						}
						else {
							isRenamed = false;
							break;
						}
					}
					else {
						Difference diffI = variableNameMismatchDifferences.get(i);
						Difference diffJ = variableNameMismatchDifferences.get(j);
						if(diffI.getFirstValue().equals(diffJ.getSecondValue())) {
							if(diffI.getSecondValue().equals(diffJ.getFirstValue())) {
								swapCount++;
							}
							else {
								isSwapped = false;
								break;
							}
						}
						else if(diffI.getSecondValue().equals(diffJ.getFirstValue())) {
							if(diffI.getFirstValue().equals(diffJ.getSecondValue())) {
								swapCount++;
							}
							else {
								isSwapped = false;
								break;
							}
						}
					}
				}
				if(isRenamed && renameCount > 1) {
					renamedVariables.add(signaturePairI);
				}
				if(isSwapped && isRenamed && swapCount > 0) {
					swappedVariables.add(signaturePairI);
				}
			}
		}
		Set<BindingSignaturePair> variables = new LinkedHashSet<BindingSignaturePair>();
		variables.addAll(renamedVariables);
		variables.addAll(swappedVariables);
		return variables;
	}

	public List<PreconditionViolation> getPreconditionViolations() {
		return preconditionViolations;
	}

	public Set<PlainVariable> getVariablesToBeReturnedG1() {
		return variablesToBeReturnedG1;
	}

	public Set<PlainVariable> getVariablesToBeReturnedG2() {
		return variablesToBeReturnedG2;
	}

	private Set<PlainVariable> variablesToBeReturned(PDG pdg, Set<PDGNode> mappedNodes) {
		Set<PDGNode> remainingNodes = new TreeSet<PDGNode>();
		Iterator<GraphNode> iterator = pdg.getNodeIterator();
		while(iterator.hasNext()) {
			PDGNode pdgNode = (PDGNode)iterator.next();
			if(!mappedNodes.contains(pdgNode)) {
				remainingNodes.add(pdgNode);
			}
		}
		Set<PlainVariable> variablesToBeReturned = new LinkedHashSet<PlainVariable>();
		for(PDGNode remainingNode : remainingNodes) {
			Iterator<GraphEdge> incomingDependenceIt = remainingNode.getIncomingDependenceIterator();
			while(incomingDependenceIt.hasNext()) {
				PDGDependence dependence = (PDGDependence)incomingDependenceIt.next();
				if(dependence instanceof PDGDataDependence) {
					PDGDataDependence dataDependence = (PDGDataDependence)dependence;
					PDGNode srcNode = (PDGNode)dataDependence.getSrc();
					if(mappedNodes.contains(srcNode) && dataDependence.getData() instanceof PlainVariable) {
						PlainVariable variable = (PlainVariable)dataDependence.getData();
						if(!variable.isField())
							variablesToBeReturned.add(variable);
					}
				}
			}
		}
		return variablesToBeReturned;
	}

	private void checkPreconditionsAboutReturnedVariables() {
		//if the returned variables are more than one, the precondition is violated
		if(variablesToBeReturnedG1.size() > 1 || variablesToBeReturnedG2.size() > 1) {
			PreconditionViolation violation = new ReturnedVariablePreconditionViolation(variablesToBeReturnedG1, variablesToBeReturnedG2,
					PreconditionViolationType.MULTIPLE_RETURNED_VARIABLES);
			preconditionViolations.add(violation);
		}
		else if(variablesToBeReturnedG1.size() == 1 && variablesToBeReturnedG2.size() == 1) {
			PlainVariable returnedVariable1 = variablesToBeReturnedG1.iterator().next();
			PlainVariable returnedVariable2 = variablesToBeReturnedG2.iterator().next();
			if(!returnedVariable1.getVariableType().equals(returnedVariable2.getVariableType())) {
				PreconditionViolation violation = new ReturnedVariablePreconditionViolation(variablesToBeReturnedG1, variablesToBeReturnedG2,
						PreconditionViolationType.SINGLE_RETURNED_VARIABLE_WITH_DIFFERENT_TYPES);
				preconditionViolations.add(violation);
			}
		}
		else if((variablesToBeReturnedG1.size() == 1 && variablesToBeReturnedG2.size() == 0) ||
				(variablesToBeReturnedG1.size() == 0 && variablesToBeReturnedG2.size() == 1)) {
			PreconditionViolation violation = new ReturnedVariablePreconditionViolation(variablesToBeReturnedG1, variablesToBeReturnedG2,
					PreconditionViolationType.UNEQUAL_NUMBER_OF_RETURNED_VARIABLES);
			preconditionViolations.add(violation);
		}
	}

	private void conditionalReturnStatement(NodeMapping nodeMapping, PDGNode node) {
		CFGNode cfgNode = node.getCFGNode();
		if(cfgNode instanceof CFGExitNode) {
			ReturnStatement returnStatement = (ReturnStatement)cfgNode.getASTStatement();
			if(returnStatement.getExpression() == null) {
				PreconditionViolation violation = new StatementPreconditionViolation(node.getStatement(),
						PreconditionViolationType.CONDITIONAL_RETURN_STATEMENT);
				nodeMapping.addPreconditionViolation(violation);
				preconditionViolations.add(violation);
			}
		}
	}

	private void branchStatementWithInnermostLoop(NodeMapping nodeMapping, PDGNode node, Set<PDGNode> mappedNodes) {
		CFGNode cfgNode = node.getCFGNode();
		if(cfgNode instanceof CFGBreakNode) {
			CFGBreakNode breakNode = (CFGBreakNode)cfgNode;
			CFGNode innerMostLoopNode = breakNode.getInnerMostLoopNode();
			if(innerMostLoopNode != null && !mappedNodes.contains(innerMostLoopNode.getPDGNode())) {
				PreconditionViolation violation = new StatementPreconditionViolation(node.getStatement(),
						PreconditionViolationType.BREAK_STATEMENT_WITHOUT_LOOP);
				nodeMapping.addPreconditionViolation(violation);
				preconditionViolations.add(violation);
			}
		}
		else if(cfgNode instanceof CFGContinueNode) {
			CFGContinueNode continueNode = (CFGContinueNode)cfgNode;
			CFGNode innerMostLoopNode = continueNode.getInnerMostLoopNode();
			if(innerMostLoopNode != null && !mappedNodes.contains(innerMostLoopNode.getPDGNode())) {
				PreconditionViolation violation = new StatementPreconditionViolation(node.getStatement(),
						PreconditionViolationType.CONTINUE_STATEMENT_WITHOUT_LOOP);
				nodeMapping.addPreconditionViolation(violation);
				preconditionViolations.add(violation);
			}
		}
	}

	private void checkCloneStructureNodeForPreconditions(CloneStructureNode node) {
		if(node.getMapping() != null)
			checkPreconditions(node);
		for(CloneStructureNode child : node.getChildren()) {
			checkCloneStructureNodeForPreconditions(child);
		}
	}

	private void checkPreconditions(CloneStructureNode node) {
		Set<BindingSignaturePair> renamedVariables = getRenamedVariables();
		TreeSet<PDGNode> removableNodesG1 = getRemovableNodesG1();
		TreeSet<PDGNode> removableNodesG2 = getRemovableNodesG2();
		NodeMapping nodeMapping = node.getMapping();
		for(ASTNodeDifference difference : nodeMapping.getNodeDifferences()) {
			AbstractExpression abstractExpression1 = difference.getExpression1();
			Expression expression1 = abstractExpression1.getExpression();
			AbstractExpression abstractExpression2 = difference.getExpression2();
			Expression expression2 = abstractExpression2.getExpression();
			if(!renamedVariables.contains(difference.getBindingSignaturePair()) && !isVariableWithTypeMismatchDifference(expression1, expression2, difference)) {
				if(!isParameterizableExpression(removableNodesG1, abstractExpression1, pdg1.getVariableDeclarationsInMethod(), iCompilationUnit1)) {
					PreconditionViolation violation = new ExpressionPreconditionViolation(difference.getExpression1(),
							PreconditionViolationType.EXPRESSION_DIFFERENCE_CANNOT_BE_PARAMETERIZED);
					nodeMapping.addPreconditionViolation(violation);
					preconditionViolations.add(violation);
					IMethodBinding methodBinding = getMethodBinding(expression1);
					if(methodBinding != null) {
						int methodModifiers = methodBinding.getModifiers();
						if((methodModifiers & Modifier.PRIVATE) != 0) {
							String message = "Inline private method " + methodBinding.getName();
							violation.addSuggestion(message);
						}
					}
				}
				if(!isParameterizableExpression(removableNodesG2, abstractExpression2, pdg2.getVariableDeclarationsInMethod(), iCompilationUnit2)) {
					PreconditionViolation violation = new ExpressionPreconditionViolation(difference.getExpression2(),
							PreconditionViolationType.EXPRESSION_DIFFERENCE_CANNOT_BE_PARAMETERIZED);
					nodeMapping.addPreconditionViolation(violation);
					preconditionViolations.add(violation);
					IMethodBinding methodBinding = getMethodBinding(expression2);
					if(methodBinding != null) {
						int methodModifiers = methodBinding.getModifiers();
						if((methodModifiers & Modifier.PRIVATE) != 0) {
							String message = "Inline private method " + methodBinding.getName();
							violation.addSuggestion(message);
						}
					}
				}
			}
			if(difference.containsDifferenceType(DifferenceType.SUBCLASS_TYPE_MISMATCH)) {
				if(nodeMapping instanceof PDGNodeMapping) {
					PDGNodeMapping pdgNodeMapping = (PDGNodeMapping)nodeMapping;
					Set<IMethodBinding> methods1 = new LinkedHashSet<IMethodBinding>();
					ITypeBinding typeBinding1 = difference.getExpression1().getExpression().resolveTypeBinding();
					findMethodsCalledFromType(typeBinding1, pdgNodeMapping.getNodeG1(), methods1);
					
					Set<IMethodBinding> methods2 = new LinkedHashSet<IMethodBinding>();
					ITypeBinding typeBinding2 = difference.getExpression2().getExpression().resolveTypeBinding();
					findMethodsCalledFromType(typeBinding2, pdgNodeMapping.getNodeG2(), methods2);
					
					if(!typeBinding1.isEqualTo(typeBinding2)) {
						ITypeBinding commonSuperType = ASTNodeMatcher.commonSuperType(typeBinding1, typeBinding2);
						if(commonSuperType != null) {
							Set<String> commonSuperTypeMembers = new LinkedHashSet<String>();
							for(IMethodBinding methodBinding1 : methods1) {
								for(IMethodBinding methodBinding2 : methods2) {
									if(MethodCallAnalyzer.equalSignature(methodBinding1, methodBinding2)) {
										IMethodBinding[] declaredMethods = commonSuperType.getDeclaredMethods();
										boolean commonSuperTypeMethodFound = false;
										for(IMethodBinding commonSuperTypeMethod : declaredMethods) {
											if(MethodCallAnalyzer.equalSignature(methodBinding1, commonSuperTypeMethod)) {
												commonSuperTypeMethodFound = true;
												break;
											}
										}
										if(!commonSuperTypeMethodFound) {
											commonSuperTypeMembers.add(methodBinding1.toString());
										}
										break;
									}
								}
							}
							if(!commonSuperTypeMembers.isEmpty()) {
								PreconditionViolation violation = new DualExpressionWithCommonSuperTypePreconditionViolation(difference.getExpression1(), difference.getExpression2(),
										PreconditionViolationType.INFEASIBLE_UNIFICATION_DUE_TO_MISSING_MEMBERS_IN_THE_COMMON_SUPERCLASS,
										commonSuperType.getQualifiedName(), commonSuperTypeMembers);
								nodeMapping.addPreconditionViolation(violation);
								preconditionViolations.add(violation);
							}
						}
					}
				}
			}
			if(difference.containsDifferenceType(DifferenceType.VARIABLE_TYPE_MISMATCH)) {
				PreconditionViolation violation = new DualExpressionPreconditionViolation(difference.getExpression1(), difference.getExpression2(),
						PreconditionViolationType.INFEASIBLE_UNIFICATION_DUE_TO_VARIABLE_TYPE_MISMATCH);
				nodeMapping.addPreconditionViolation(violation);
				preconditionViolations.add(violation);
				ITypeBinding typeBinding1 = expression1.resolveTypeBinding();
				ITypeBinding typeBinding2 = expression2.resolveTypeBinding();
				if(!typeBinding1.isPrimitive() && !typeBinding2.isPrimitive()) {
					String message = "Make classes " + typeBinding1.getQualifiedName() + " and " + typeBinding2.getQualifiedName() + " extend a common superclass";
					violation.addSuggestion(message);
				}
			}
		}
		if(nodeMapping instanceof PDGNodeGap) {
			if(nodeMapping.getNodeG1() != null && !nodeMapping.isAdvancedMatch()) {
				processNonMappedNode(nodeMapping, nodeMapping.getNodeG1(), removableNodesG1, nonMappedPDGNodesG1MovableBeforeAndAfter,
						nonMappedPDGNodesG1MovableBefore, nonMappedPDGNodesG1MovableAfter, variablesToBeReturnedG1);
			}
			if(nodeMapping.getNodeG2() != null && !nodeMapping.isAdvancedMatch()) {
				processNonMappedNode(nodeMapping, nodeMapping.getNodeG2(), removableNodesG2, nonMappedPDGNodesG2MovableBeforeAndAfter,
						nonMappedPDGNodesG2MovableBefore, nonMappedPDGNodesG2MovableAfter, variablesToBeReturnedG2);
			}
		}
		if(nodeMapping instanceof PDGNodeMapping) {
			branchStatementWithInnermostLoop(nodeMapping, nodeMapping.getNodeG1(), removableNodesG1);
			branchStatementWithInnermostLoop(nodeMapping, nodeMapping.getNodeG2(), removableNodesG2);
			//skip examining the conditional return precondition, if the number of examined nodes is equal to the number of PDG nodes
			if(getAllNodesInSubTreePDG1().size() != pdg1.getNodes().size()) {
				conditionalReturnStatement(nodeMapping, nodeMapping.getNodeG1());
			}
			if(getAllNodesInSubTreePDG2().size() != pdg2.getNodes().size()) {
				conditionalReturnStatement(nodeMapping, nodeMapping.getNodeG2());
			}
		}
	}

	private boolean isVariableWithTypeMismatchDifference(Expression expression1, Expression expression2, ASTNodeDifference difference) {
		if(expression1 instanceof SimpleName && expression2 instanceof SimpleName) {
			SimpleName simpleName1 = (SimpleName)expression1;
			SimpleName simpleName2 = (SimpleName)expression2;
			IBinding binding1 = simpleName1.resolveBinding();
			IBinding binding2 = simpleName2.resolveBinding();
			//check if both simpleNames refer to variables
			if(binding1.getKind() == IBinding.VARIABLE && binding2.getKind() == IBinding.VARIABLE) {
				List<Difference> differences = difference.getDifferences();
				if(differences.size() == 1) {
					Difference diff = differences.get(0);
					if(diff.getType().equals(DifferenceType.SUBCLASS_TYPE_MISMATCH) || diff.getType().equals(DifferenceType.VARIABLE_TYPE_MISMATCH)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void findMethodsCalledFromType(ITypeBinding typeBinding, PDGNode pdgNode, Set<IMethodBinding> methods) {
		Set<MethodInvocationObject> accessedMethods = new LinkedHashSet<MethodInvocationObject>();
		AbstractStatement abstractStatement = pdgNode.getStatement();
		if(abstractStatement instanceof StatementObject) {
			StatementObject statement = (StatementObject)abstractStatement;
			accessedMethods.addAll(statement.getMethodInvocations());
		}
		else if(abstractStatement instanceof CompositeStatementObject) {
			CompositeStatementObject composite = (CompositeStatementObject)abstractStatement;
			accessedMethods.addAll(composite.getMethodInvocationsInExpressions());
			if(composite instanceof TryStatementObject) {
				TryStatementObject tryStatement = (TryStatementObject)composite;
				List<CatchClauseObject> catchClauses = tryStatement.getCatchClauses();
				for(CatchClauseObject catchClause : catchClauses) {
					accessedMethods.addAll(catchClause.getBody().getMethodInvocations());
				}
				if(tryStatement.getFinallyClause() != null) {
					accessedMethods.addAll(tryStatement.getFinallyClause().getMethodInvocations());
				}
			}
		}
		for(MethodInvocationObject invocation : accessedMethods) {
			IMethodBinding methodBinding = invocation.getMethodInvocation().resolveMethodBinding();
			if(methodBinding.getDeclaringClass().isEqualTo(typeBinding)) {
				methods.add(methodBinding);
			}
		}
	}

	private void processNonMappedNode(NodeMapping nodeMapping, PDGNode node, TreeSet<PDGNode> removableNodes,
			TreeSet<PDGNode> movableBeforeAndAfter, TreeSet<PDGNode> movableBefore, TreeSet<PDGNode> movableAfter, Set<PlainVariable> returnedVariables) {
		boolean movableNonMappedNodeBeforeFirstMappedNode = movableNonMappedNodeBeforeFirstMappedNode(removableNodes, node);
		boolean movableNonMappedNodeAfterLastMappedNode = movableNonMappedNodeAfterLastMappedNode(removableNodes, node, returnedVariables);
		if(!movableNonMappedNodeBeforeFirstMappedNode && !movableNonMappedNodeAfterLastMappedNode) {
			PreconditionViolation violation = new StatementPreconditionViolation(node.getStatement(),
					PreconditionViolationType.UNMATCHED_STATEMENT_CANNOT_BE_MOVED_BEFORE_OR_AFTER_THE_EXTRACTED_CODE);
			nodeMapping.addPreconditionViolation(violation);
			preconditionViolations.add(violation);
		}
		else if(movableNonMappedNodeBeforeFirstMappedNode && movableNonMappedNodeAfterLastMappedNode) {
			movableBeforeAndAfter.add(node);
		}
		else if(movableNonMappedNodeBeforeFirstMappedNode) {
			movableBefore.add(node);
		}
		else if(movableNonMappedNodeAfterLastMappedNode) {
			movableAfter.add(node);
		}
		CFGNode cfgNode = node.getCFGNode();
		if(cfgNode instanceof CFGBreakNode) {
			PreconditionViolation violation = new StatementPreconditionViolation(node.getStatement(),
					PreconditionViolationType.UNMATCHED_BREAK_STATEMENT);
			nodeMapping.addPreconditionViolation(violation);
			preconditionViolations.add(violation);
		}
		else if(cfgNode instanceof CFGContinueNode) {
			PreconditionViolation violation = new StatementPreconditionViolation(node.getStatement(),
					PreconditionViolationType.UNMATCHED_CONTINUE_STATEMENT);
			nodeMapping.addPreconditionViolation(violation);
			preconditionViolations.add(violation);
		}
		else if(cfgNode instanceof CFGExitNode) {
			PreconditionViolation violation = new StatementPreconditionViolation(node.getStatement(),
					PreconditionViolationType.UNMATCHED_RETURN_STATEMENT);
			nodeMapping.addPreconditionViolation(violation);
			preconditionViolations.add(violation);
		}
		else if(cfgNode instanceof CFGThrowNode) {
			PreconditionViolation violation = new StatementPreconditionViolation(node.getStatement(),
					PreconditionViolationType.UNMATCHED_THROW_STATEMENT);
			nodeMapping.addPreconditionViolation(violation);
			preconditionViolations.add(violation);
		}
	}
	private void processNonMappedNodesMovableBeforeAndAfter() {
		for(PDGNode nodeG1 : nonMappedPDGNodesG1MovableBeforeAndAfter) {
			boolean movableNonMappedNodeBeforeNonMappedNodesMovableAfter = movableNonMappedNodeBeforeNonMappedNodesMovableAfter(nonMappedPDGNodesG1MovableAfter, nodeG1);
			if(movableNonMappedNodeBeforeNonMappedNodesMovableAfter) {
				nonMappedPDGNodesG1MovableBefore.add(nodeG1);
			}
			else {
				nonMappedPDGNodesG1MovableAfter.add(nodeG1);
			}
		}
		for(PDGNode nodeG2 : nonMappedPDGNodesG2MovableBeforeAndAfter) {
			boolean movableNonMappedNodeBeforeNonMappedNodesMovableAfter = movableNonMappedNodeBeforeNonMappedNodesMovableAfter(nonMappedPDGNodesG2MovableAfter, nodeG2);
			if(movableNonMappedNodeBeforeNonMappedNodesMovableAfter) {
				nonMappedPDGNodesG2MovableBefore.add(nodeG2);
			}
			else {
				nonMappedPDGNodesG2MovableAfter.add(nodeG2);
			}
		}
	}
	private boolean movableNonMappedNodeBeforeNonMappedNodesMovableAfter(TreeSet<PDGNode> nonMappedNodes, PDGNode nonMappedNode) {
		Iterator<GraphEdge> incomingDependenceIterator = nonMappedNode.getIncomingDependenceIterator();
		while(incomingDependenceIterator.hasNext()) {
			PDGDependence dependence = (PDGDependence)incomingDependenceIterator.next();
			if(dependence instanceof PDGAbstractDataDependence) {
				PDGAbstractDataDependence dataDependence = (PDGAbstractDataDependence)dependence;
				PDGNode srcPDGNode = (PDGNode)dataDependence.getSrc();
				if(nonMappedNodes.contains(srcPDGNode)) {
					return false;
				}
				//examine if it is a self-loop edge due to a loop-carried dependence
				if(srcPDGNode.equals(nonMappedNode)) {
					if(dataDependence.isLoopCarried() && nonMappedNodes.contains(dataDependence.getLoop().getPDGNode())) {
						return false;
					}
				}
			}
		}
		for(PDGNode dstPDGNode : nonMappedNodes) {
			if(dstPDGNode.isControlDependentOnNode(nonMappedNode)) {
				return false;
			}
		}
		return true;
	}
	//precondition: non-mapped statement can be moved before the first mapped statement
	private boolean movableNonMappedNodeBeforeFirstMappedNode(TreeSet<PDGNode> mappedNodes, PDGNode nonMappedNode) {
		Iterator<GraphEdge> incomingDependenceIterator = nonMappedNode.getIncomingDependenceIterator();
		while(incomingDependenceIterator.hasNext()) {
			PDGDependence dependence = (PDGDependence)incomingDependenceIterator.next();
			if(dependence instanceof PDGAbstractDataDependence) {
				PDGAbstractDataDependence dataDependence = (PDGAbstractDataDependence)dependence;
				PDGNode srcPDGNode = (PDGNode)dataDependence.getSrc();
				if(mappedNodes.contains(srcPDGNode)) {
					return false;
				}
				//examine if it is a self-loop edge due to a loop-carried dependence
				if(srcPDGNode.equals(nonMappedNode)) {
					if(dataDependence.isLoopCarried() && mappedNodes.contains(dataDependence.getLoop().getPDGNode())) {
						return false;
					}
				}
			}
		}
		return true;
	}
	//precondition: non-mapped statement can be moved after the last mapped statement
	private boolean movableNonMappedNodeAfterLastMappedNode(TreeSet<PDGNode> mappedNodes, PDGNode nonMappedNode, Set<PlainVariable> returnedVariables) {
		Iterator<GraphEdge> outgoingDependenceIterator = nonMappedNode.getOutgoingDependenceIterator();
		while(outgoingDependenceIterator.hasNext()) {
			PDGDependence dependence = (PDGDependence)outgoingDependenceIterator.next();
			if(dependence instanceof PDGAbstractDataDependence) {
				PDGAbstractDataDependence dataDependence = (PDGAbstractDataDependence)dependence;
				PDGNode dstPDGNode = (PDGNode)dataDependence.getDst();
				if(mappedNodes.contains(dstPDGNode)) {
					return false;
				}
				//examine if it is a self-loop edge due to a loop-carried dependence
				if(dstPDGNode.equals(nonMappedNode)) {
					if(dataDependence.isLoopCarried() && mappedNodes.contains(dataDependence.getLoop().getPDGNode())) {
						return false;
					}
				}
			}
		}
		Iterator<GraphEdge> incomingDependenceIterator = nonMappedNode.getIncomingDependenceIterator();
		while(incomingDependenceIterator.hasNext()) {
			PDGDependence dependence = (PDGDependence)incomingDependenceIterator.next();
			if(dependence instanceof PDGAbstractDataDependence) {
				PDGAbstractDataDependence dataDependence = (PDGAbstractDataDependence)dependence;
				PDGNode srcPDGNode = (PDGNode)dataDependence.getSrc();
				if(mappedNodes.contains(srcPDGNode)) {
					AbstractVariable data = dataDependence.getData();
					if(data instanceof PlainVariable) {
						PlainVariable plainVariable = (PlainVariable)data;
						if(!plainVariable.isField() && !returnedVariables.contains(plainVariable)) {
							return false;
						}
					}
					else if(data instanceof CompositeVariable) {
						CompositeVariable composite = (CompositeVariable)data;
						PlainVariable initial = composite.getInitialVariable();
						if(!initial.isField() && !returnedVariables.contains(initial)) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}
	//precondition: differences in expressions should be parameterizable
	private boolean isParameterizableExpression(TreeSet<PDGNode> mappedNodes, AbstractExpression initialAbstractExpression,
			Set<VariableDeclaration> variableDeclarationsInMethod, ICompilationUnit iCompilationUnit) {
		Expression initialExpression = initialAbstractExpression.getExpression();
		Expression expr = ASTNodeDifference.getParentExpressionOfMethodNameOrTypeName(initialExpression);
		PDGExpression pdgExpression;
		if(!expr.equals(initialExpression)) {
			ASTInformationGenerator.setCurrentITypeRoot(iCompilationUnit);
			AbstractExpression tempExpression = new AbstractExpression(expr);
			pdgExpression = new PDGExpression(tempExpression, variableDeclarationsInMethod);
		}
		else {
			pdgExpression = new PDGExpression(initialAbstractExpression, variableDeclarationsInMethod);
		}
		//find mapped node containing the expression
		PDGNode nodeContainingExpression = null;
		for(PDGNode node : mappedNodes) {
			if(isExpressionUnderStatement(expr, node.getASTStatement())) {
				nodeContainingExpression = node;
				break;
			}
		}
		if(nodeContainingExpression != null) {
			TreeSet<PDGNode> nodes = new TreeSet<PDGNode>(mappedNodes);
			nodes.remove(nodeContainingExpression);
			Iterator<GraphEdge> incomingDependenceIterator = nodeContainingExpression.getIncomingDependenceIterator();
			while(incomingDependenceIterator.hasNext()) {
				PDGDependence dependence = (PDGDependence)incomingDependenceIterator.next();
				if(dependence instanceof PDGAbstractDataDependence) {
					PDGAbstractDataDependence abstractDependence = (PDGAbstractDataDependence)dependence;
					PDGNode srcPDGNode = (PDGNode)abstractDependence.getSrc();
					if(nodes.contains(srcPDGNode)) {
						if(dependence instanceof PDGDataDependence) {
							PDGDataDependence dataDependence = (PDGDataDependence)dependence;
							//check if pdgExpression is using dataDependence.data
							if(pdgExpression.usesLocalVariable(dataDependence.getData()))
								return false;
						}
						else if(dependence instanceof PDGAntiDependence) {
							PDGAntiDependence antiDependence = (PDGAntiDependence)dependence;
							//check if pdgExpression is defining dataDependence.data
							if(pdgExpression.definesLocalVariable(antiDependence.getData()))
								return false;
						}
						else if(dependence instanceof PDGOutputDependence) {
							PDGOutputDependence outputDependence = (PDGOutputDependence)dependence;
							//check if pdgExpression is defining dataDependence.data
							if(pdgExpression.definesLocalVariable(outputDependence.getData()))
								return false;
						}
					}
					//examine if it is a self-loop edge due to a loop-carried dependence
					if(srcPDGNode.equals(nodeContainingExpression)) {
						if(abstractDependence.isLoopCarried() && nodes.contains(abstractDependence.getLoop().getPDGNode())) {
							if(pdgExpression.definesLocalVariable(abstractDependence.getData()) ||
									pdgExpression.usesLocalVariable(abstractDependence.getData())) {
								return false;
							}
						}
					}
				}
			}
		}
		else {
			//the expression is within the catch/finally blocks of a try statement
			for(PDGNode mappedNode : mappedNodes) {
				Iterator<AbstractVariable> definedVariableIterator = mappedNode.getDefinedVariableIterator();
				while(definedVariableIterator.hasNext()) {
					AbstractVariable definedVariable = definedVariableIterator.next();
					if(pdgExpression.usesLocalVariable(definedVariable) || pdgExpression.definesLocalVariable(definedVariable))
						return false;
				}
				Iterator<AbstractVariable> usedVariableIterator = mappedNode.getUsedVariableIterator();
				while(usedVariableIterator.hasNext()) {
					AbstractVariable usedVariable = usedVariableIterator.next();
					if(pdgExpression.definesLocalVariable(usedVariable))
						return false;
				}
			}
		}
		return true;
	}

	private boolean isExpressionUnderStatement(ASTNode expression, Statement statement) {
		ASTNode parent = expression.getParent();
		if(parent.equals(statement))
			return true;
		if(!(parent instanceof Statement))
			return isExpressionUnderStatement(parent, statement);
		else
			return false;
	}
	
	private IMethodBinding getMethodBinding(Expression expression) {
		if(expression instanceof SimpleName) {
			SimpleName simpleName = (SimpleName)expression;
			IBinding binding = simpleName.resolveBinding();
			if(binding != null && binding.getKind() == IBinding.METHOD) {
				return (IMethodBinding)binding;
			}
		}
		else if(expression instanceof MethodInvocation) {
			MethodInvocation methodInvocation = (MethodInvocation)expression;
			return methodInvocation.resolveMethodBinding();
		}
		else if(expression instanceof SuperMethodInvocation) {
			SuperMethodInvocation methodInvocation = (SuperMethodInvocation)expression;
			return methodInvocation.resolveMethodBinding();
		}
		return null;
	}
	
	public CloneType getCloneType() {
		if(getMaximumStateWithMinimumDifferences() != null) {
			int nodeDifferences = getNodeDifferences().size();
			if(nodeDifferences == 0 && nonMappedNodesG1.size() == 0 && nonMappedNodesG2.size() == 0) {
				return CloneType.TYPE_1;
			}
			if(nodeDifferences > 0 && nonMappedNodesG1.size() == 0 && nonMappedNodesG2.size() == 0) {
				return CloneType.TYPE_2;
			}
			if(nonMappedNodesG1.size() > 0 || nonMappedNodesG2.size() > 0) {
				if(isType3(getCloneStructureRoot())) {
					return CloneType.TYPE_3;
				}
				else {
					return CloneType.TYPE_2;
				}
			}
		}
		return CloneType.UNKNOWN;
	}
	
	private boolean isType3(CloneStructureNode node) {
		Map<Integer, PDGNodeGap> gapMap = new LinkedHashMap<Integer, PDGNodeGap>();
		int counter = 0;
		for(CloneStructureNode child : node.getChildren()) {
			if(child.getMapping() instanceof PDGNodeGap) {
				gapMap.put(counter, (PDGNodeGap)child.getMapping());
			}
			counter++;
		}
		if(!gapMap.isEmpty()) {
			int gaps1 = 0;
			int gaps2 = 0;
			for(Integer key : gapMap.keySet()) {
				PDGNodeGap nodeGap = gapMap.get(key);
				if(nodeGap.getNodeG1() != null) {
					gaps1++;
				}
				if(nodeGap.getNodeG2() != null) {
					gaps2++;
				}
			}
			if(gaps1 != gaps2) {
				return true;
			}
		}
		for(CloneStructureNode child : node.getChildren()) {
			boolean type3 = isType3(child);
			if(type3) {
				return true;
			}
		}
		return false;
	}
}
