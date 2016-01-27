package fr.idl.fixit.processors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import spoon.processing.AbstractProcessor;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;

public class BinaryOperatorProcessor extends AbstractProcessor<CtBinaryOperator<?>> {
	
	public static List<BinaryOperatorKind> binaryOperatorNumber = new ArrayList<BinaryOperatorKind>();
	public static List<BinaryOperatorKind> binaryOperatorBoolean = new ArrayList<BinaryOperatorKind>();
	public static List<BinaryOperatorKind> binaryOperatorShift = new ArrayList<BinaryOperatorKind>();
	public static List<BinaryOperatorKind> binaryOperatorLogic = new ArrayList<BinaryOperatorKind>();
	public static boolean alreadyMuted = false;
	/* Map qui permet de retrouver le nombre de tentative restante par CtBinaryOperator*/
	public static Map<Integer, Integer> nbrTentativeRestanteParCtBinaryOperator = new HashMap<Integer, Integer>();
	
	/* Map qui permet de conserver l operateur binaire initial ou celui qui a eu le meilleur score aux tests*/
	public static Map<Integer, BinaryOperatorKind> bestBinaryOperator;
	
	/*Flag qui permet de savoir si le lancement des tests apres la mutation a genere de meilleurs resultats*/
	public static boolean better;
	
	public static Integer lastMuted = null;
	public static BinaryOperatorKind lastKindMuted = null;
	/*Flag qui permet de savoir si toutes les mutations possibles ont ete realisees*/
	public static boolean terminated = false;
	static {
		
		binaryOperatorShift.add(BinaryOperatorKind.USR);
		binaryOperatorShift.add(BinaryOperatorKind.SR);
		binaryOperatorShift.add(BinaryOperatorKind.SL);

		
		binaryOperatorLogic.add(BinaryOperatorKind.AND);
		binaryOperatorLogic.add(BinaryOperatorKind.OR);
		binaryOperatorLogic.add(BinaryOperatorKind.BITAND);
		binaryOperatorLogic.add(BinaryOperatorKind.BITOR);
		binaryOperatorLogic.add(BinaryOperatorKind.BITXOR);
		
		binaryOperatorBoolean.add(BinaryOperatorKind.LT);
		binaryOperatorBoolean.add(BinaryOperatorKind.LE);
		binaryOperatorBoolean.add(BinaryOperatorKind.GT);
		binaryOperatorBoolean.add(BinaryOperatorKind.GE);
		binaryOperatorBoolean.add(BinaryOperatorKind.EQ);
		binaryOperatorBoolean.add(BinaryOperatorKind.NE);
		
		
		binaryOperatorNumber.add(BinaryOperatorKind.DIV);
		binaryOperatorNumber.add(BinaryOperatorKind.PLUS);
		binaryOperatorNumber.add(BinaryOperatorKind.MINUS);
		binaryOperatorNumber.add(BinaryOperatorKind.MOD);
		binaryOperatorNumber.add(BinaryOperatorKind.MUL);
		
	}
	


	public void process(CtBinaryOperator<?> binaryOperatorLine) {
		CtClass<?> parent = binaryOperatorLine.getParent(CtClass.class);
		String parentSimpleName = parent.getSimpleName();

		if(terminated){
			return;
		}
		if(better){
			bestBinaryOperator.put(lastMuted, lastKindMuted);
			better = false;
			lastMuted = null;
			lastKindMuted = null;
		}
		
		if(checkAllMuted()){
			List<CtBinaryOperator> listeBinaryOperator = Query.getElements(parent, new TypeFilter<CtBinaryOperator>(CtBinaryOperator.class));
			if(listeBinaryOperator != null){
				for(CtBinaryOperator<?> binaryOperator : listeBinaryOperator){
					BinaryOperatorKind operateur = bestBinaryOperator.get(generateIdentifier(binaryOperator));
					if(operateur != null){
						binaryOperator.setKind(operateur);
					}
				}
			}
			return;
		}
		
		Integer nbrTentative = nbrTentativeRestanteParCtBinaryOperator.get(generateIdentifier(binaryOperatorLine));
		if(nbrTentative != null && nbrTentative == -1){
			return;
		}
		if(!(parentSimpleName.contains("Test")) && !(parentSimpleName.contains("Obj"))){
			
			if(binaryOperatorBoolean.contains(binaryOperatorLine.getKind())){
				nextMutation(binaryOperatorLine, binaryOperatorBoolean);
			}else if(binaryOperatorNumber.contains(binaryOperatorLine.getKind()) && !(binaryOperatorLine.getLeftHandOperand().getType().getActualClass().equals(String.class))){
				nextMutation(binaryOperatorLine, binaryOperatorNumber);
			}else if(binaryOperatorLogic.contains(binaryOperatorLine.getKind())){
				nextMutation(binaryOperatorLine, binaryOperatorLogic);
			}else if(binaryOperatorShift.contains(binaryOperatorLine.getKind())){
				nextMutation(binaryOperatorLine, binaryOperatorShift);
			}else{
				return;
			}
			
		}
		
	}



	/* Permet d effectuer la mutation suivante en se basant sur le nombre de tentative restante
	 * 
	 * */
	private void nextMutation(CtBinaryOperator<?> binaryOperatorLine, List<BinaryOperatorKind> binaryOperatorContainer) {
		int identifiant = generateIdentifier(binaryOperatorLine);
		Integer nbrTentativeRestante = nbrTentativeRestanteParCtBinaryOperator.get(identifiant);
		if(nbrTentativeRestante == null){
			nbrTentativeRestante = binaryOperatorContainer.size()-1;
			nbrTentativeRestanteParCtBinaryOperator.put(identifiant, nbrTentativeRestante);
			bestBinaryOperator.put(identifiant, binaryOperatorLine.getKind());
		}
		if(!alreadyMuted){
			binaryOperatorLine.setKind(binaryOperatorContainer.get(nbrTentativeRestante));
			lastKindMuted = binaryOperatorContainer.get(nbrTentativeRestante);
			nbrTentativeRestante--;
			nbrTentativeRestanteParCtBinaryOperator.put(identifiant,nbrTentativeRestante);
			alreadyMuted = true;
			lastMuted = identifiant;
		}
	}
	
	/* genere un identifiant unique pour stocker les valeurs dans les maps*/
	private int generateIdentifier(CtBinaryOperator<?> operator){
		int longueurMembreGauche = operator.getLeftHandOperand().toString().length();
		int longueurMembreDroite = operator.getRightHandOperand().toString().length();
		Integer longueur = operator.getLeftHandOperand().toString().charAt(longueurMembreGauche-1)+operator.getRightHandOperand().toString().charAt(longueurMembreDroite-1);
		return (operator.getPosition().hashCode()+longueur.hashCode())+(operator.toString().split(" ").length)+longueur.hashCode();
	}

	private boolean checkAllMuted() {
		if(nbrTentativeRestanteParCtBinaryOperator.size() > 1){
			for(Integer tentativeRestante : nbrTentativeRestanteParCtBinaryOperator.values()){
				if(tentativeRestante > -1){
					return false;
				}
			}
			terminated = true;
			return true;
		}
		
		return false;
	}



	public static void raz() {
		BinaryOperatorProcessor.better = false;
		BinaryOperatorProcessor.terminated = false;
		BinaryOperatorProcessor.bestBinaryOperator = new HashMap<Integer, BinaryOperatorKind>();
		BinaryOperatorProcessor.nbrTentativeRestanteParCtBinaryOperator = new HashMap<Integer, Integer>();	
		BinaryOperatorProcessor.alreadyMuted = false;
		BinaryOperatorProcessor.lastMuted = null;
		BinaryOperatorProcessor.lastKindMuted = null;
	}

}
