package xadd;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import camdp.HierarchicalParser;
import util.DevNullPrintStream;
import util.Timer;
import xadd.XADD.XADDLeafMinOrMax;


public class DeepNeuralNet {
	
    public static double CVAR_LB = -10;
    public static double CVAR_UB = 10;
    public static String fileName = "./src/xadd/dnn_files/DNN_2_4.txt";
    // List of weights for each layer, from input unit 1 to output units
    public static  ArrayList<List<String>> weights1 = new ArrayList<List<String>>();
    
    // List of weights for each layer, from input unit 2 to output units
    public static ArrayList<List<String>> weights2 = new ArrayList<List<String>>();

    public static void main(String[] args) throws Exception {
    	
    	readFile();
    	
    	XADD xadd_context = new XADD();
    	int h1 = 0;
    	int h2 = 0;
    	int zero = ParseXADDString(xadd_context, "([0])");
    	int output;
    	int fx2;
    	Timer timer = new Timer();
    	//xadd_context.getGraph(zeroXADD).launchViewer("0");
    	for (int depth = 0; depth < weights1.size(); depth++) {
    		List<String> w1 = weights1.get(depth);
    		List<String> w2 = weights2.get(depth);
    		if (depth == 0) {
    			String xaddStr1 = w1.get(0) + " * x1" + " + " + w2.get(0)  + " * x2";
    			String xaddStr2 = w1.get(1) + " * x1" + " + " + w2.get(1)  + " * x2";  
    			int f1 = ParseXADDString(xadd_context, "([" + xaddStr1 + "])");
    			int f2 = ParseXADDString(xadd_context, "([" + xaddStr2 + "])");
    			h1 = xadd_context.apply(zero, f1, XADD.MAX);
    			h2 = xadd_context.apply(zero, f2, XADD.MAX);
    			//xadd_context.getGraph(h1).launchViewer("h1");
    			//xadd_context.getGraph(h2).launchViewer("h2");
    		}
    		else if (depth < weights1.size() - 1) {
    			int w_11 = ParseXADDString(xadd_context, "([" + w1.get(0) + "])");
    			int w_21 = ParseXADDString(xadd_context, "([" + w2.get(0) + "])");
    			int w_12 = ParseXADDString(xadd_context, "([" + w1.get(1) + "])");
    			int w_22 = ParseXADDString(xadd_context, "([" + w2.get(1) + "])");    			
    			int t11 = xadd_context.apply(w_11, h1, XADD.PROD);
    			int t21 = xadd_context.apply(w_21, h2, XADD.PROD);
    			int t12 = xadd_context.apply(w_12, h1, XADD.PROD);
    			int t22 = xadd_context.apply(w_22, h2, XADD.PROD);    			
    			int f1 = xadd_context.apply(t11, t21, XADD.SUM);
    			int f2 = xadd_context.apply(t12, t22, XADD.SUM);
    			h1 = xadd_context.apply(zero, f1, XADD.MAX);
    			h2 = xadd_context.apply(zero, f2, XADD.MAX);
    		}
    		else {
    			int w_11 = ParseXADDString(xadd_context, "([" + w1.get(0) + "])");
    			int w_21 = ParseXADDString(xadd_context, "([" + w2.get(0) + "])");  			
    			int t11 = xadd_context.apply(w_11, h1, XADD.PROD);
    			int t21 = xadd_context.apply(w_21, h2, XADD.PROD);  			
    			int f1 = xadd_context.apply(t11, t21, XADD.SUM);
    			output = xadd_context.apply(zero, f1, XADD.MAX);
    			//xadd_context.getGraph(output).launchViewer("output");
    			fx2 = maxOutCVar(xadd_context, output, "x1", CVAR_LB, CVAR_UB);
    			//xadd_context.getGraph(fx2).launchViewer("fx2");
    		}
    		
    	}
    	
    	long value = timer.GetCurElapsedTime();
    	System.out.println(value + "ms");
		
    }
    
    public static void readFile() {
        String line = null;

        try {
            FileReader fileReader = 
                new FileReader(fileName);

            BufferedReader bufferedReader = 
                new BufferedReader(fileReader);
            
            int index = 1;
            
            List<String> w1, w2;
            while((line = bufferedReader.readLine()) != null) {
                System.out.println(line);
                if (index == 1) {
               	 index++;
               	 continue;
                }
                else if (index == 2) {
               	 w1 = Arrays.asList(line.split(","));
               	 weights1.add(w1);
               	 index++;
                }
                else if (index == 3){
               	 w2 = Arrays.asList(line.split(","));
               	 weights2.add(w2);
               	 index++;
                }
                else {
               	 index = 1;
                }
            }

            bufferedReader.close();         
        }
        catch(FileNotFoundException ex) {
            System.out.println(
                "Unable to open file '" + 
                fileName + "'");                
        }
        catch(IOException ex) {
            System.out.println(
                "Error reading file '" 
                + fileName + "'");              
        }        	
    }
    
    public static int ParseXADDString(XADD xadd_context, String s) {
    	ArrayList l = HierarchicalParser.ParseString(s);
        // System.out.println("Parsed: " + l);
        return XADDParseUtils.BuildCanonicalXADD(xadd_context, (ArrayList) l.get(0));
    }
    
    public static int maxOutCVar(XADD _context, int obj, String cvar, double lb, double ub) {
        XADDLeafMinOrMax max = _context.new XADDLeafMinOrMax(cvar, lb, ub, true /* is_max */, new DevNullPrintStream());
        _context.reduceProcessXADDLeaf(obj, max, true);
        int result = _context.reduceLP(max._runningResult);
        return result;
    }	
	
}
