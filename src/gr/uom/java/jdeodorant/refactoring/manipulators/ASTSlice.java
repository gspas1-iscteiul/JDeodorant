package gr.uom.java.jdeodorant.refactoring.manipulators;

import gr.uom.java.ast.decomposition.cfg.BasicBlock;
import gr.uom.java.ast.decomposition.cfg.PDGNode;
import gr.uom.java.ast.decomposition.cfg.PDGObjectSliceUnion;
import gr.uom.java.ast.decomposition.cfg.PDGSlice;
import gr.uom.java.ast.decomposition.cfg.PDGSliceUnion;
import gr.uom.java.ast.decomposition.cfg.AbstractVariable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jface.text.Position;

public class ASTSlice {
	private TypeDeclaration sourceTypeDeclaration;
	private MethodDeclaration sourceMethodDeclaration;
	private Set<PDGNode> sliceNodes;
	private Set<Statement> sliceStatements;
	private Set<Statement> removableStatements;
	private Set<Statement> duplicatedStatements;
	private VariableDeclaration localVariableCriterion;
	private Set<VariableDeclaration> passedParameters;
	private Statement extractedMethodInvocationInsertionStatement;
	private String extractedMethodName;
	private boolean declarationOfVariableCriterionBelongsToSliceNodes;
	private boolean declarationOfVariableCriterionBelongsToRemovableNodes;
	private IFile iFile;
	private BasicBlock boundaryBlock;
	private boolean isObjectSlice;
	private double averageNumberOfExtractedStatementsInGroup;
	private int maximumNumberOfExtractedStatementsInGroup;
	private double averageNumberOfDuplicatedStatementsInGroup;
	private double averageDuplicationRatioInGroup;
	
	public ASTSlice(PDGSlice pdgSlice) {
		this.sourceMethodDeclaration = pdgSlice.getMethod().getMethodDeclaration();
		this.sourceTypeDeclaration = (TypeDeclaration)sourceMethodDeclaration.getParent();
		this.sliceNodes = pdgSlice.getSliceNodes();
		this.sliceStatements = new LinkedHashSet<Statement>();
		for(PDGNode node : sliceNodes) {
			sliceStatements.add(node.getASTStatement());
		}
		this.removableStatements = new LinkedHashSet<Statement>();
		for(PDGNode node : pdgSlice.getRemovableNodes()) {
			removableStatements.add(node.getASTStatement());
		}
		this.duplicatedStatements = new LinkedHashSet<Statement>(sliceStatements);
		this.duplicatedStatements.removeAll(removableStatements);
		Set<VariableDeclaration> variableDeclarationsAndAccessedFields = pdgSlice.getVariableDeclarationsAndAccessedFieldsInMethod();
		AbstractVariable criterion = pdgSlice.getLocalVariableCriterion();
		for(VariableDeclaration variableDeclaration : variableDeclarationsAndAccessedFields) {
			if(variableDeclaration.resolveBinding().getKey().equals(criterion.getVariableBindingKey())) {
				this.localVariableCriterion = variableDeclaration;
				break;
			}
		}
		this.passedParameters = new LinkedHashSet<VariableDeclaration>();
		for(AbstractVariable variable : pdgSlice.getPassedParameters()) {
			for(VariableDeclaration variableDeclaration : variableDeclarationsAndAccessedFields) {
				if(variableDeclaration.resolveBinding().getKey().equals(variable.getVariableBindingKey())) {
					passedParameters.add(variableDeclaration);
					break;
				}
			}
		}
		this.extractedMethodInvocationInsertionStatement = pdgSlice.getExtractedMethodInvocationInsertionNode().getASTStatement();
		this.extractedMethodName = localVariableCriterion.getName().getIdentifier();
		this.declarationOfVariableCriterionBelongsToSliceNodes = pdgSlice.declarationOfVariableCriterionBelongsToSliceNodes();
		this.declarationOfVariableCriterionBelongsToRemovableNodes = pdgSlice.declarationOfVariableCriterionBelongsToRemovableNodes();
		this.iFile = pdgSlice.getIFile();
		this.boundaryBlock = pdgSlice.getBoundaryBlock();
		this.isObjectSlice = false;
	}

	public ASTSlice(PDGSliceUnion pdgSliceUnion) {
		this.sourceMethodDeclaration = pdgSliceUnion.getMethod().getMethodDeclaration();
		this.sourceTypeDeclaration = (TypeDeclaration)sourceMethodDeclaration.getParent();
		this.sliceNodes = pdgSliceUnion.getSliceNodes();
		this.sliceStatements = new LinkedHashSet<Statement>();
		for(PDGNode node : sliceNodes) {
			sliceStatements.add(node.getASTStatement());
		}
		this.removableStatements = new LinkedHashSet<Statement>();
		for(PDGNode node : pdgSliceUnion.getRemovableNodes()) {
			removableStatements.add(node.getASTStatement());
		}
		this.duplicatedStatements = new LinkedHashSet<Statement>(sliceStatements);
		this.duplicatedStatements.removeAll(removableStatements);
		Set<VariableDeclaration> variableDeclarationsAndAccessedFields = pdgSliceUnion.getVariableDeclarationsAndAccessedFieldsInMethod();
		AbstractVariable criterion = pdgSliceUnion.getLocalVariableCriterion();
		for(VariableDeclaration variableDeclaration : variableDeclarationsAndAccessedFields) {
			if(variableDeclaration.resolveBinding().getKey().equals(criterion.getVariableBindingKey())) {
				this.localVariableCriterion = variableDeclaration;
				break;
			}
		}
		this.passedParameters = new LinkedHashSet<VariableDeclaration>();
		for(AbstractVariable variable : pdgSliceUnion.getPassedParameters()) {
			for(VariableDeclaration variableDeclaration : variableDeclarationsAndAccessedFields) {
				if(variableDeclaration.resolveBinding().getKey().equals(variable.getVariableBindingKey())) {
					passedParameters.add(variableDeclaration);
					break;
				}
			}
		}
		this.extractedMethodInvocationInsertionStatement = pdgSliceUnion.getExtractedMethodInvocationInsertionNode().getASTStatement();
		this.extractedMethodName = localVariableCriterion.getName().getIdentifier();
		this.declarationOfVariableCriterionBelongsToSliceNodes = pdgSliceUnion.declarationOfVariableCriterionBelongsToSliceNodes();
		this.declarationOfVariableCriterionBelongsToRemovableNodes = pdgSliceUnion.declarationOfVariableCriterionBelongsToRemovableNodes();
		this.iFile = pdgSliceUnion.getIFile();
		this.boundaryBlock = pdgSliceUnion.getBoundaryBlock();
		this.isObjectSlice = false;
	}

	public ASTSlice(PDGObjectSliceUnion pdgObjectSliceUnion) {
		this.sourceMethodDeclaration = pdgObjectSliceUnion.getMethod().getMethodDeclaration();
		this.sourceTypeDeclaration = (TypeDeclaration)sourceMethodDeclaration.getParent();
		this.sliceNodes = pdgObjectSliceUnion.getSliceNodes();
		this.sliceStatements = new LinkedHashSet<Statement>();
		for(PDGNode node : sliceNodes) {
			sliceStatements.add(node.getASTStatement());
		}
		this.removableStatements = new LinkedHashSet<Statement>();
		for(PDGNode node : pdgObjectSliceUnion.getRemovableNodes()) {
			removableStatements.add(node.getASTStatement());
		}
		this.duplicatedStatements = new LinkedHashSet<Statement>(sliceStatements);
		this.duplicatedStatements.removeAll(removableStatements);
		Set<VariableDeclaration> variableDeclarationsAndAccessedFields = pdgObjectSliceUnion.getVariableDeclarationsAndAccessedFieldsInMethod();
		AbstractVariable criterion = pdgObjectSliceUnion.getObjectReference();
		for(VariableDeclaration variableDeclaration : variableDeclarationsAndAccessedFields) {
			if(variableDeclaration.resolveBinding().getKey().equals(criterion.getVariableBindingKey())) {
				this.localVariableCriterion = variableDeclaration;
				break;
			}
		}
		this.passedParameters = new LinkedHashSet<VariableDeclaration>();
		for(AbstractVariable variable : pdgObjectSliceUnion.getPassedParameters()) {
			for(VariableDeclaration variableDeclaration : variableDeclarationsAndAccessedFields) {
				if(variableDeclaration.resolveBinding().getKey().equals(variable.getVariableBindingKey())) {
					passedParameters.add(variableDeclaration);
					break;
				}
			}
		}
		this.extractedMethodInvocationInsertionStatement = pdgObjectSliceUnion.getExtractedMethodInvocationInsertionNode().getASTStatement();
		this.extractedMethodName = localVariableCriterion.getName().getIdentifier();
		this.declarationOfVariableCriterionBelongsToSliceNodes = pdgObjectSliceUnion.declarationOfObjectReferenceBelongsToSliceNodes();
		this.declarationOfVariableCriterionBelongsToRemovableNodes = pdgObjectSliceUnion.declarationOfObjectReferenceBelongsToRemovableNodes();
		this.iFile = pdgObjectSliceUnion.getIFile();
		this.boundaryBlock = pdgObjectSliceUnion.getBoundaryBlock();
		this.isObjectSlice = true;
	}

	public TypeDeclaration getSourceTypeDeclaration() {
		return sourceTypeDeclaration;
	}

	public MethodDeclaration getSourceMethodDeclaration() {
		return sourceMethodDeclaration;
	}

	public VariableDeclaration getLocalVariableCriterion() {
		return localVariableCriterion;
	}

	public Set<VariableDeclaration> getPassedParameters() {
		return passedParameters;
	}

	public Set<PDGNode> getSliceNodes() {
		return sliceNodes;
	}

	public Set<Statement> getSliceStatements() {
		return sliceStatements;
	}

	public Set<Statement> getRemovableStatements() {
		return removableStatements;
	}

	public Statement getExtractedMethodInvocationInsertionStatement() {
		return extractedMethodInvocationInsertionStatement;
	}

	public String getExtractedMethodName() {
		return extractedMethodName;
	}

	public void setExtractedMethodName(String extractedMethodName) {
		this.extractedMethodName = extractedMethodName;
	}

	public boolean declarationOfVariableCriterionBelongsToSliceNodes() {
		return declarationOfVariableCriterionBelongsToSliceNodes;
	}

	public boolean declarationOfVariableCriterionBelongsToRemovableNodes() {
		return declarationOfVariableCriterionBelongsToRemovableNodes;
	}

	public IFile getIFile() {
		return iFile;
	}

	public BasicBlock getBoundaryBlock() {
		return boundaryBlock;
	}

	public boolean isObjectSlice() {
		return isObjectSlice;
	}

	public Object[] getHighlightPositions() {
		Map<Position, String> annotationMap = new LinkedHashMap<Position, String>();
		Map<Position, Boolean> duplicationMap = new LinkedHashMap<Position, Boolean>();
		List<PDGNode> sliceNodeList = new ArrayList<PDGNode>(sliceNodes);
		int i = 0;
		for(Statement statement : sliceStatements) {
			PDGNode sliceNode = sliceNodeList.get(i);
			if(statement instanceof IfStatement) {
				IfStatement ifStatement = (IfStatement)statement;
				Expression ifExpression = ifStatement.getExpression();
				Position position = new Position(ifExpression.getStartPosition(), ifExpression.getLength());
				annotationMap.put(position, sliceNode.getAnnotation());
				if(duplicatedStatements.contains(statement))
					duplicationMap.put(position, true);
				else
					duplicationMap.put(position, false);
			}
			else if(statement instanceof WhileStatement) {
				WhileStatement whileStatement = (WhileStatement)statement;
				Expression whileExpression = whileStatement.getExpression();
				Position position = new Position(whileExpression.getStartPosition(), whileExpression.getLength());
				annotationMap.put(position, sliceNode.getAnnotation());
				if(duplicatedStatements.contains(statement))
					duplicationMap.put(position, true);
				else
					duplicationMap.put(position, false);
			}
			else if(statement instanceof ForStatement) {
				ForStatement forStatement = (ForStatement)statement;
				List<Expression> initializers = forStatement.initializers();
				for(Expression expression : initializers) {
					Position initializerPosition = new Position(expression.getStartPosition(), expression.getLength());
					annotationMap.put(initializerPosition, sliceNode.getAnnotation());
					if(duplicatedStatements.contains(statement))
						duplicationMap.put(initializerPosition, true);
					else
						duplicationMap.put(initializerPosition, false);
				}
				Expression forExpression = forStatement.getExpression();
				if(forExpression != null) {
					Position position = new Position(forExpression.getStartPosition(), forExpression.getLength());
					annotationMap.put(position, sliceNode.getAnnotation());
					if(duplicatedStatements.contains(statement))
						duplicationMap.put(position, true);
					else
						duplicationMap.put(position, false);
				}
				List<Expression> updaters = forStatement.updaters();
				for(Expression expression : updaters) {
					Position updaterPosition = new Position(expression.getStartPosition(), expression.getLength());
					annotationMap.put(updaterPosition, sliceNode.getAnnotation());
					if(duplicatedStatements.contains(statement))
						duplicationMap.put(updaterPosition, true);
					else
						duplicationMap.put(updaterPosition, false);
				}
			}
			else if(statement instanceof EnhancedForStatement) {
				EnhancedForStatement enhancedForStatement = (EnhancedForStatement)statement;
				SingleVariableDeclaration parameter = enhancedForStatement.getParameter();
				Position parameterPosition = new Position(parameter.getStartPosition(), parameter.getLength());
				annotationMap.put(parameterPosition, sliceNode.getAnnotation());
				if(duplicatedStatements.contains(statement))
					duplicationMap.put(parameterPosition, true);
				else
					duplicationMap.put(parameterPosition, false);
				Expression expression = enhancedForStatement.getExpression();
				Position expressionPosition = new Position(expression.getStartPosition(), expression.getLength());
				annotationMap.put(expressionPosition, sliceNode.getAnnotation());
				if(duplicatedStatements.contains(statement))
					duplicationMap.put(expressionPosition, true);
				else
					duplicationMap.put(expressionPosition, false);
			}
			else if(statement instanceof DoStatement) {
				DoStatement doStatement = (DoStatement)statement;
				Expression doExpression = doStatement.getExpression();
				Position position = new Position(doExpression.getStartPosition(), doExpression.getLength());
				annotationMap.put(position, sliceNode.getAnnotation());
				if(duplicatedStatements.contains(statement))
					duplicationMap.put(position, true);
				else
					duplicationMap.put(position, false);
			}
			else if(statement instanceof SwitchStatement) {
				SwitchStatement switchStatement = (SwitchStatement)statement;
				Expression switchExpression = switchStatement.getExpression();
				Position position = new Position(switchExpression.getStartPosition(), switchExpression.getLength());
				annotationMap.put(position, sliceNode.getAnnotation());
				if(duplicatedStatements.contains(statement))
					duplicationMap.put(position, true);
				else
					duplicationMap.put(position, false);
			}
			else {
				Position position = new Position(statement.getStartPosition(), statement.getLength());
				annotationMap.put(position, sliceNode.getAnnotation());
				if(duplicatedStatements.contains(statement))
					duplicationMap.put(position, true);
				else
					duplicationMap.put(position, false);
			}
			i++;
		}
		return new Object[] {annotationMap, duplicationMap};
	}

	public double getAverageNumberOfExtractedStatementsInGroup() {
		return averageNumberOfExtractedStatementsInGroup;
	}

	public void setAverageNumberOfExtractedStatementsInGroup(double averageNumberOfExtractedStatementsInGroup) {
		this.averageNumberOfExtractedStatementsInGroup = averageNumberOfExtractedStatementsInGroup;
	}

	public int getMaximumNumberOfExtractedStatementsInGroup() {
		return maximumNumberOfExtractedStatementsInGroup;
	}

	public void setMaximumNumberOfExtractedStatementsInGroup(int maximumNumberOfExtractedStatementsInGroup) {
		this.maximumNumberOfExtractedStatementsInGroup = maximumNumberOfExtractedStatementsInGroup;
	}

	public double getAverageNumberOfDuplicatedStatementsInGroup() {
		return averageNumberOfDuplicatedStatementsInGroup;
	}

	public void setAverageNumberOfDuplicatedStatementsInGroup(double averageNumberOfDuplicatedStatementsInGroup) {
		this.averageNumberOfDuplicatedStatementsInGroup = averageNumberOfDuplicatedStatementsInGroup;
	}

	public double getAverageDuplicationRatioInGroup() {
		return averageDuplicationRatioInGroup;
	}

	public void setAverageDuplicationRatioInGroup(double averageDuplicationRatioInGroup) {
		this.averageDuplicationRatioInGroup = averageDuplicationRatioInGroup;
	}
}
