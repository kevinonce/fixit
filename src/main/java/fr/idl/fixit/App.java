package fr.idl.fixit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import fr.idl.fixit.processors.BinaryOperatorProcessor;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;

/**
 * Hello world!
 *
 */
public class App 
{
	private static final String WHITEBOX_TEST = "WhiteboxTest";
	private static final String BLACKBOX_TEST = "BlackboxTest";
	private static final String SPOON_REPERTOIRE= "spooned";
	private static final String SPOON_CLASS_REPERTOIRE="spooned-classes";
	private static final String FILE_RESULT_NAME ="result.txt";
	private static BinaryOperatorProcessor binaryOperatorProcessor = new BinaryOperatorProcessor();
	
	public static List<String> listSourceFiles;
	public static List<String> listTestFiles;
	
	private static List<AbstractProcessor<?>> listProcessors  = new LinkedList<AbstractProcessor<?>>();
	
	private static void launchSpoon(String projectPath, AbstractProcessor<?> p) throws Exception{
		
		String repertoireCible = projectPath;
		String repertoireDestinationClasse = "";
		String pattern = Pattern.quote(File.separator);
		String [] repertoireSplit = projectPath.split(pattern);
		if(repertoireSplit.length > 2){
			repertoireCible = SPOON_REPERTOIRE+File.separator+generateRepertoireName(projectPath);
			repertoireDestinationClasse = SPOON_CLASS_REPERTOIRE+File.separator+generateRepertoireName(projectPath);
		}else{
			repertoireDestinationClasse = SPOON_CLASS_REPERTOIRE+File.separator+repertoireSplit[repertoireSplit.length-1];
			
		}
		String[] spoonArgs = { "-i", projectPath, "--compile", "-o", repertoireCible, "-d" ,repertoireDestinationClasse };
		
			Launcher l = new Launcher();
			if(p!=null){
				l.addProcessor(p);
			}
			l.run(spoonArgs);
		
	}
	
	/* transforme le nom d un fichier en nom de classe*/
	private static String convertToClassName(String file) {
		String pattern = Pattern.quote(File.separator);
		int ind = file.split(pattern).length;
		return file.split(pattern)[ind-2] + "." + file.split(pattern)[ind -1].replace(".java", "");
	}
	
	private static String generateRepertoireName(String projectPath) {
		String pattern = Pattern.quote(File.separator);
		String[] chaines = projectPath.split(pattern);
		return chaines[chaines.length-4]+"-"+chaines[chaines.length-3]+"-"+chaines[chaines.length-2];
	}
	

	/*Permet de recuperer tous les fichiers java d un projet ou uniquement le fichier de nom classUnderTest il n'est pas null*/
	private static List<String> findJavaFiles(String path, String classUnderTest){
		File[] files = new File(path).listFiles();
		List<String> pathToSources = new ArrayList<String>();
		for(File f : files){
			if(f.isDirectory())
				pathToSources.addAll(findJavaFiles(f.getAbsolutePath(),classUnderTest));
			else if((classUnderTest == null && f.getName().endsWith(".java")) ||
					(classUnderTest != null && f.getName().contains(classUnderTest) && f.getName().endsWith(".java"))){
				pathToSources.add(f.getAbsolutePath());
			}
		}
		
		return pathToSources;
	}
	
	private static void deleteClassFiles(String path){
		File[] files = new File(path).listFiles();
		if(files != null){
			for(File f : files){
				if(!f.isDirectory() && f.getName().contains(".class"))
					f.delete();
				else if(f.isDirectory())
					deleteClassFiles(f.getAbsolutePath());
			}
		}
		
	}
	
	public static String getWhiteTestClassNameFromProject(String sourcePath){
		List<String> tests = findJavaFiles(sourcePath + "/test/java",null);
		for(String test : tests){
			if(test.contains(WHITEBOX_TEST)){
				return convertToClassName(test);
			}
		}
		
		return null;
	}
	
	public static String getBlackTestClassNameFromProject(String sourcePath){
		List<String> tests = findJavaFiles(sourcePath + "/test/java",null);
		for(String test : tests){
			if(test.contains(BLACKBOX_TEST)){
				return convertToClassName(test);
			}
		}
		
		return null;
	}
	
	public static void addResultToFile(String folderName, int nbrWhiteFailInitial, int nbrBlackFailInitial, int nbrWhiteFailFinal, int nbrBlackFailFinal){
		String ligne = "projet: "+folderName+" init: w["+nbrWhiteFailInitial+"]b["+nbrBlackFailInitial+"] final: w["+nbrWhiteFailFinal+"]b["+nbrBlackFailFinal+"]";
		addLigneToResult(ligne);	
	}
	
	public static void addDateToFile(){
		DateFormat fullDateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL,DateFormat.FULL);
		addLigneToResult(fullDateFormat.format(new Date())+"\n\n");
	}

	private static void addLigneToResult(String ligne) {
		FileWriter dw = null;
		try {
			dw = new FileWriter(FILE_RESULT_NAME,true);
			dw.write(ligne);
			dw.flush();
			dw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally {
			try {
				dw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args) throws Exception {
		
		long start = System.currentTimeMillis();
		String folder = "";
		if(args.length == 0){
			System.out.println("vous devez préciser le chemin vers le dossier src de votre projet");
			return;
		}else{
			folder = args[0];
		}

		TestLauncher testLauncher = new TestLauncher();
		listProcessors.add(new BinaryOperatorProcessor());
		addDateToFile();
				
			BinaryOperatorProcessor.raz();
			
			String repertoireClasseName = SPOON_CLASS_REPERTOIRE+File.separator+generateRepertoireName(folder);
			String whiteTestCurrent = getWhiteTestClassNameFromProject(folder);
			String blackTestCurrent = getBlackTestClassNameFromProject(folder);
			deleteClassFiles(repertoireClasseName);
			launchSpoon(folder,null);
			int nbrFailWhiteInit = testLauncher.runTests(whiteTestCurrent,repertoireClasseName);
			int nbrFailBlackInit =testLauncher.runTests(blackTestCurrent, repertoireClasseName);

			int lowestFail = nbrFailWhiteInit == 0  ? nbrFailBlackInit : nbrFailWhiteInit;

			if(lowestFail > 0){
				//tant qu il reste des possiblites de mutation on boucle sur les projets generes par spoon
				while(BinaryOperatorProcessor.terminated != true){
					BinaryOperatorProcessor.alreadyMuted = false;
					
					deleteClassFiles(repertoireClasseName);
					launchSpoon(folder, binaryOperatorProcessor);
					
					int nbrFailAfterSpoon = testLauncher.runTests(whiteTestCurrent,repertoireClasseName);
					if(nbrFailAfterSpoon < lowestFail){
						System.out.println("correction detectee !" +nbrFailAfterSpoon+ " < "+lowestFail);
						lowestFail = nbrFailAfterSpoon;
						BinaryOperatorProcessor.better = true;
					}
				}

			}
			int nbrfailWhiteFinal = testLauncher.runTests(whiteTestCurrent,repertoireClasseName);
			int nbrfailBlackFinal = testLauncher.runTests(blackTestCurrent, repertoireClasseName);
			
			if((nbrFailWhiteInit != 0 && nbrfailWhiteFinal == 0) ||  nbrFailBlackInit != 0 &&  nbrfailBlackFinal == 0){
				addResultToFile(folder,nbrFailWhiteInit,nbrFailBlackInit,nbrfailWhiteFinal,nbrfailBlackFinal);
			}

		System.out.println("\n\nTime taken : " + (System.currentTimeMillis() - start) / 1000 + " sec");
	}
}
