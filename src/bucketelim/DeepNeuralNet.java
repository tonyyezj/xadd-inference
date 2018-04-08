package bucketelim;

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
import xadd.XADD;
import xadd.XADDParseUtils;
import xadd.XADD.XADDLeafMinOrMax;


public class DeepNeuralNet {
	
    public static double CVAR_LB = -10;
    public static double CVAR_UB = 10;
    public static String fileName = "./src/xadd/dnn_files/DNN_2_7.txt";
    // List of weights for each layer, from input unit 1 to output units
    public static  ArrayList<List<String>> weights1 = new ArrayList<List<String>>();
    
    // List of weights for each layer, from input unit 2 to output units
    public static ArrayList<List<String>> weights2 = new ArrayList<List<String>>();
    public static ArrayList<List<String>> weights3 = new ArrayList<List<String>>();
    public static ArrayList<List<String>> weights4 = new ArrayList<List<String>>();
    public static int width = 2;
    // File in which to append the runtime to
    public static String resultsFile = "./src/xadd/dnn_files/runtimes_2.txt";

    public static void main(String[] args) throws Exception {
    	
    	readFile();
    	
    	XADD xadd_context = new XADD();
    	int h1 = 0;
    	int h2 = 0;
    	int h3 = 0;
    	int h4 = 0;
    	int zero = ParseXADDString(xadd_context, "([0])");
    	int output;
    	int projected;

    	Timer timer = new Timer();
    	//xadd_context.getGraph(zeroXADD).launchViewer("0");
    	for (int depth = 0; depth < weights1.size(); depth++) {
    		List<String> w1 = weights1.get(depth);
    		List<String> w2 = weights2.get(depth);
    		List<String> w3 = (width > 2 && depth > 0) ? weights3.get(depth-1) : null;
    		List<String> w4 = (width > 3 && depth > 0) ? weights4.get(depth-1) : null;
    		if (depth == 0) {
    			String xaddStr1 = w1.get(0) + " * x1" + " + " + w2.get(0)  + " * x2";
    			String xaddStr2 = w1.get(1) + " * x1" + " + " + w2.get(1)  + " * x2";  
    			int f1 = ParseXADDString(xadd_context, "([" + xaddStr1 + "])");
    			int f2 = ParseXADDString(xadd_context, "([" + xaddStr2 + "])");
    			h1 = xadd_context.apply(zero, f1, XADD.MAX);
    			h2 = xadd_context.apply(zero, f2, XADD.MAX);
    			//xadd_context.getGraph(h1).launchViewer("h1");
    			//xadd_context.getGraph(h2).launchViewer("h2");
    			if (width > 2) {
    				String xaddStr3 = w1.get(2) + " * x1" + " + " + w2.get(2)  + " * x2";
    				int f3 = ParseXADDString(xadd_context, "([" + xaddStr3 + "])");
    				h3 = xadd_context.apply(zero, f3, XADD.MAX);
    			}
    			if (width == 4) {
    				String xaddStr4 = w1.get(3) + " * x1" + " + " + w2.get(3)  + " * x2";
    				int f4 = ParseXADDString(xadd_context, "([" + xaddStr4 + "])");
    				h4 = xadd_context.apply(zero, f4, XADD.MAX);
    			}
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
    			
    			if (width == 2) {

	    			h1 = xadd_context.apply(zero, f1, XADD.MAX);
	    			h2 = xadd_context.apply(zero, f2, XADD.MAX);
	    			h1 = xadd_context.reduceLP(h1);
	    			h2 = xadd_context.reduceLP(h2);
    			}
    			else if (width > 3) {
	    			int w_31 = ParseXADDString(xadd_context, "([" + w3.get(0) + "])");
	    			int w_32 = ParseXADDString(xadd_context, "([" + w3.get(1) + "])");
	    			int w_33 = ParseXADDString(xadd_context, "([" + w3.get(2) + "])");
	    			int w_13 = ParseXADDString(xadd_context, "([" + w1.get(2) + "])");
	    			int w_23 = ParseXADDString(xadd_context, "([" + w2.get(2) + "])");  
	    			int t31 = xadd_context.apply(w_31, h3, XADD.PROD);
	    			int t32 = xadd_context.apply(w_32, h3, XADD.PROD);
	    			int t33 = xadd_context.apply(w_33, h3, XADD.PROD);
	    			int t13 = xadd_context.apply(w_13, h1, XADD.PROD);
	    			int t23 = xadd_context.apply(w_23, h2, XADD.PROD);
	    			f1 = xadd_context.apply(f1, t31, XADD.SUM);
	    			f2 = xadd_context.apply(f2, t32, XADD.SUM);
	    			int f3 = xadd_context.apply(t13, t23, XADD.SUM);
	    			f3 = xadd_context.apply(f3, t33, XADD.SUM);
    	    		if (width == 3) {    	
    	    			h1 = xadd_context.apply(zero, f1, XADD.MAX);
    	    			h2 = xadd_context.apply(zero, f2, XADD.MAX);
    	    			h3 = xadd_context.apply(zero, f3, XADD.MAX);
    	    			h1 = xadd_context.reduceLP(h1);
    	    			h2 = xadd_context.reduceLP(h2);
    	    			h3 = xadd_context.reduceLP(h3);
    				}	
    				
        			if (width == 4) {
    	    			int w_41 = ParseXADDString(xadd_context, "([" + w4.get(0) + "])");
    	    			int w_42 = ParseXADDString(xadd_context, "([" + w4.get(1) + "])");
    	    			int w_43 = ParseXADDString(xadd_context, "([" + w4.get(2) + "])");
    	    			int w_44 = ParseXADDString(xadd_context, "([" + w4.get(3) + "])");
    	    			int w_14 = ParseXADDString(xadd_context, "([" + w1.get(3) + "])");
    	    			int w_24 = ParseXADDString(xadd_context, "([" + w2.get(3) + "])");    
    	    			int w_34 = ParseXADDString(xadd_context, "([" + w3.get(3) + "])");  
    	    			int t41 = xadd_context.apply(w_41, h4, XADD.PROD);
    	    			int t42 = xadd_context.apply(w_42, h4, XADD.PROD);
    	    			int t43 = xadd_context.apply(w_43, h4, XADD.PROD);
    	    			int t44 = xadd_context.apply(w_44, h4, XADD.PROD);
    	    			int t14 = xadd_context.apply(w_14, h1, XADD.PROD);
    	    			int t24 = xadd_context.apply(w_24, h2, XADD.PROD);
    	    			int t34 = xadd_context.apply(w_34, h3, XADD.PROD);
    	    			f1 = xadd_context.apply(f1, t41, XADD.SUM);
    	    			f2 = xadd_context.apply(f2, t42, XADD.SUM);
    	    			f3 = xadd_context.apply(f3, t43, XADD.SUM);
    	    			int f4 = xadd_context.apply(t14, t24, XADD.SUM);
    	    			f4 = xadd_context.apply(f4, t34, XADD.SUM);   
    	    			f4 = xadd_context.apply(f4, t44, XADD.SUM);  
    	    			h1 = xadd_context.apply(zero, f1, XADD.MAX);
    	    			h2 = xadd_context.apply(zero, f2, XADD.MAX);
    	    			h3 = xadd_context.apply(zero, f4, XADD.MAX);
    	    			h4  = xadd_context.apply(zero, f4, XADD.MAX);
    	    			h1 = xadd_context.reduceLP(h1);
    	    			h2 = xadd_context.reduceLP(h2);
    	    			h3 = xadd_context.reduceLP(h3);
    	    			h4 = xadd_context.reduceLP(h4);
        			}
    			}
    			

    		}
    		else {
    			int w_11 = ParseXADDString(xadd_context, "([" + w1.get(0) + "])");
    			int w_21 = ParseXADDString(xadd_context, "([" + w2.get(0) + "])");  			
    			int t11 = xadd_context.apply(w_11, h1, XADD.PROD);
    			int t21 = xadd_context.apply(w_21, h2, XADD.PROD);  
    			int f1 = xadd_context.apply(t11, t21, XADD.SUM);
    			if (width == 2) {
    				
    			}
    			else if (width > 2) {
    				int w_31 = ParseXADDString(xadd_context, "([" + w3.get(0) + "])");
    				int t31 = xadd_context.apply(w_31, h3, XADD.PROD); 
    				f1 = xadd_context.apply(f1, t31, XADD.SUM);
  
    				if (width == 4) {
    					int w_41 = ParseXADDString(xadd_context, "([" + w4.get(0) + "])");
    					int t41 = xadd_context.apply(w_41, h4, XADD.PROD); 
    					f1 = xadd_context.apply(f1, t41, XADD.SUM);
    				}
    			}
    			f1 = xadd_context.reduceLP(f1);
    			System.out.println("f1" + xadd_context.getNodeCount(f1));
    			output = xadd_context.apply(zero, f1, XADD.MAX);
    			output = xadd_context.reduceLP(output);
    			System.out.println("after Apply" + xadd_context.getNodeCount(output));
    			//xadd_context.getGraph(output).launchViewer("output");
    			projected = maxOutCVar(xadd_context, output, "x1", CVAR_LB, CVAR_UB);
    			//xadd_context.getGraph(fx2).launchViewer("fx2");
    		}
    		
    	}
    	
    	long value = timer.GetCurElapsedTime();
    	System.out.println(value + "ms");
		try(FileWriter fw = new FileWriter(resultsFile, true);
			    BufferedWriter bw = new BufferedWriter(fw);
			    PrintWriter out = new PrintWriter(bw))
			{
			    out.println(String.valueOf(value));
			    //more code
			} catch (IOException e) {
			    //exception handling left as an exercise for the reader
			}
    	    	
		
    }
    
    public static void readFile() {
        String line = null;

        try {
            FileReader fileReader = 
                new FileReader(fileName);

            BufferedReader bufferedReader = 
                new BufferedReader(fileReader);
            
            int index = 1;
            
            List<String> w1, w2, w3, w4;
        	boolean first = true;
            while((line = bufferedReader.readLine()) != null) {
                System.out.println(line);
                if (index == 1 && first) {
               	 index++;
               	 first = false;
               	 continue;
                }
                else if (index == 1 && !first) {
                	width = Integer.parseInt(Arrays.asList(line.split(",")).get(0));
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
                else if (index == 4 && width > 2){
                  	 w3 = Arrays.asList(line.split(","));
                  	 weights3.add(w3);
                  	 index++;
                   }
                else if (index == 5 && width > 3){
                  	 w4 = Arrays.asList(line.split(","));
                  	 weights4.add(w4);
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