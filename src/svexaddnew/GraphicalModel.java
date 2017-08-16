/* Graphical Model Structure for Symbolic Variable Elimination
 * 
 * @author Scott Sanner (ssanner@gmail.com)
 * @author Ehsan Abbasnejad
 */
package svexaddnew;

import graph.Graph;

import java.util.*;

import camdp.HierarchicalParser;

import xadd.XADD;
import xadd.XADDUtils;
import xadd.XADD.*;
import xadd.ExprLib.*;

public class GraphicalModel {

    public ArrayList<String> _alBVarsTemplate = null;
    public ArrayList<String> _alCVarsTemplate = null;
    public HashMap<String, ArrayList> _hmVar2cptTemplate = null;
    public HashMap<String, Double> _hmCVar2MinValue = null;
    public HashMap<String, Double> _hmCVar2MaxValue = null;
    public ArrayList<Factor> _alFactors = null;
    public HashSet<String> _hsVariables = null;
    public XADD _context = null;

    public class Factor {
        public int _xadd;
        public HashSet<String> _vars;
        public XADD _localContext; // Outside classes cannot see _context, so store locally

        public Factor(int xadd) {
            _xadd = xadd;
            _localContext = _context;
            _vars = _context.collectVars(_xadd);
            _hsVariables.addAll(_vars);
        }

        public String toString() {
            return _vars + ":\n" + _context.getString(_xadd);
        }
    }

    public GraphicalModel(String filename) {
        _hmVar2cptTemplate = new HashMap<String, ArrayList>();
        _alBVarsTemplate = new ArrayList<String>();
        _alCVarsTemplate = new ArrayList<String>();
        _hmCVar2MinValue = new HashMap<String, Double>();
        _hmCVar2MaxValue = new HashMap<String, Double>();
        ArrayList l = HierarchicalParser.ParseFile(filename);
        //System.out.println(l);
        parseGMTemplate(l);
    }

    public void instantiateGMTemplate(HashMap<String, ArrayList<Integer>> var2expansion) {

        _context = new XADD();
        _alFactors = new ArrayList<Factor>();
        _hsVariables = new HashSet<String>();

        // Assumptions: * at most one free index per variable
        //              * if a variable x_i uses x_i' then it will be defined by x_i'
        //                with a separate x_1... both x_i' and x_1 (or lowest index) must be present
        //              * if an index appears in a cpf, it will appear in the head variable
        //              * indices must start with letter, e.g., x_1 will be interpreted as is

        // Expand variables and ensure properly defined in XADD
        ArrayList[] both_lists = new ArrayList[]{_alBVarsTemplate, _alCVarsTemplate};
        for (ArrayList list : both_lists) {
            for (String var : (ArrayList<String>) list) {

                String split[] = var.split("_");
                if (split.length == 1 || (split.length == 2 && Character.isDigit(split[1].charAt(0)))) {
                    addXADDVar(var, list == _alBVarsTemplate);
                    int cpt_id = getXADD(var, list == _alBVarsTemplate);
                    _alFactors.add(new Factor(cpt_id));
                } else if (split.length == 2) {

                    String var_part = split[0];
                    String index = split[1];
                    ArrayList<Integer> index_values = var2expansion.get(index);
                    if (index_values == null)
                        exit("Could not find index variables for index '" + index + "' of '" + var + "'");
                    ArrayList cpt_desc = _hmVar2cptTemplate.get(var);
                    boolean prime = false;
                    if (cpt_desc == null) {
                        prime = (cpt_desc = _hmVar2cptTemplate.get(var + "'")) != null;
                        if (!prime)
                            exit("Could not find CPF for " + var + " or " + var + "'");
                    }
                    addXADDVar(prime ? var + "'" : var, list == _alBVarsTemplate);
                    int cpt_template = getXADD(prime ? var + "'" : var, list == _alBVarsTemplate);
                    //System.out.println("Template: " + var + "\n" + _context.getString(cpt_template));

                    // Process each index var
                    for (int i = 0; i < index_values.size(); i++) {
                        Integer index_value = index_values.get(i);

                        addXADDVar(var_part + "_" + index_value, list == _alBVarsTemplate);

                        if (prime && i == 0) {
                            // If using prime, get first index CPF directly from cpt lookup
                            // table (must be defined)
                            int cpt_id = getXADD(var_part + "_" + index_value, list == _alBVarsTemplate);
                            _alFactors.add(new Factor(cpt_id));
                        } else { // i > 0

                            if (prime)
                                addXADDVar(var_part + "_" + (index_value - 1), list == _alBVarsTemplate);

                            // Need to process all other variables in XADD and substitute
                            // into XADD template as needed
                            HashMap<String, ArithExpr> subst = new HashMap<String, ArithExpr>();
                            HashSet<String> all_vars = _context.collectVars(cpt_template);
                            for (String xadd_var : all_vars) {

                                if (xadd_var.endsWith("'"))
                                    xadd_var = xadd_var.substring(0, xadd_var.length() - 1);
                                String split2[] = xadd_var.split("_");
                                if (split2.length == 2) {
                                    if (prime) {
                                        subst.put(xadd_var, new VarExpr(split2[0] + "_" + (index_value - 1)));
                                        subst.put(xadd_var + "'", new VarExpr(split2[0] + "_" + index_value));
                                    } else
                                        subst.put(xadd_var, new VarExpr(split2[0] + "_" + index_value));
                                }
                            }

                            int cpt_id = _context.substitute(cpt_template, subst);
                            _alFactors.add(new Factor(cpt_id));
                        }
                    }

                } else {
                    exit("Var '" + var + "' can only have one index, it has " + split.length);
                }
            }
        }
    }

    public int getXADD(String var, boolean is_bool) {
        ArrayList cpt_desc = _hmVar2cptTemplate.get(var);
        if (cpt_desc == null)
            exit("Could not find XADD for " + var);
        int cpt_id = _context.buildCanonicalXADD(cpt_desc);
        if (is_bool) {
            // Fill in false side of diagram if boolean
            int var_index = _context.getVarIndex(_context.new BoolDec(var/* + "'"*/), false);
            if (var_index == 0)
                exit("Could not get XADD variable index for " + var);
            int high_branch = cpt_id;
            int low_branch = _context.apply(
                    _context.getTermNode(new DoubleExpr(1d)), high_branch, XADD.MINUS);
            cpt_id = _context.getINode(var_index, low_branch, high_branch);
            cpt_id = _context.makeCanonical(cpt_id);
        }
        return cpt_id;
    }

    protected void addXADDVar(String var, boolean is_bool) {

        if (is_bool)
            _context.getVarIndex(_context.new BoolDec(var), true);
        else {
            Double min_value = _hmCVar2MinValue.get(var);
            Double max_value = _hmCVar2MaxValue.get(var);
            if (min_value == null || max_value == null) {
                String split[] = var.split("_");
                if (split.length == 2) {
                    min_value = _hmCVar2MinValue.get(split[0]);
                    max_value = _hmCVar2MaxValue.get(split[0]);
                }
                if (min_value == null || max_value == null)
                    exit("Could not find min or max value for '" + var + "'");
            }
            _context._hmMinVal.put(var, min_value);
            _context._hmMaxVal.put(var, max_value);
        }
    }

    public void parseGMTemplate(ArrayList l) {

        // Set up variables
        Iterator i = l.iterator();
        Object o = i.next();
        if (!(o instanceof String) || !((String) o).equalsIgnoreCase("cvariables")) {
            exit("Missing cvariable declarations: " + o);
        }
        o = i.next();
        _alCVarsTemplate = (ArrayList<String>) ((ArrayList) o).clone();
        o = i.next();
        if (!(o instanceof String) || !((String) o).equalsIgnoreCase("min-values")) {
            exit("Missing min-values declarations: " + o);
        }
        o = i.next();
        for (int index = 0; index < _alCVarsTemplate.size(); index++) {
            if (index >= ((ArrayList) o).size())
                exit("Min-values size does not match var list size");
            String var = _alCVarsTemplate.get(index);

            String val = ((ArrayList) o).get(index).toString();
            if (!val.trim().equalsIgnoreCase("x")) try {
                double min_val = Double.parseDouble(val);
                _hmCVar2MinValue.put(var, min_val);

                // Add root mapping to: x_i -> val then x -> val as well
                String split[] = var.split("_");
                String var_root = (split.length == 2) ? split[0] : null;
                if (var_root != null)
                    _hmCVar2MinValue.put(var_root, min_val);
            } catch (NumberFormatException nfe) {
                System.out.println("\nIllegal min-value: " + var + " = " + val + " @ index " + index);
                System.exit(1);
            }
        }
        o = i.next();
        if (!(o instanceof String) || !((String) o).equalsIgnoreCase("max-values")) {
            exit("Missing max-values declarations: " + o);
        }
        o = i.next();
        for (int index = 0; index < _alCVarsTemplate.size(); index++) {
            if (index >= ((ArrayList) o).size())
                exit("Max-values size does not match var list size");
            String var = _alCVarsTemplate.get(index);
            String val = ((ArrayList) o).get(index).toString();
            if (!val.trim().equalsIgnoreCase("x")) try {
                double max_val = Double.parseDouble(val);
                _hmCVar2MaxValue.put(var, max_val);

                // Add root mapping to: x_i -> val then x -> val as well
                String split[] = var.split("_");
                String var_root = (split.length == 2) ? split[0] : null;
                if (var_root != null)
                    _hmCVar2MaxValue.put(var_root, max_val);
            } catch (NumberFormatException nfe) {
                System.out.println("\nIllegal max-value: " + var + " = " + val + " @ index " + index);
                System.exit(1);
            }
        }
        o = i.next();
        if (!(o instanceof String) || !((String) o).equalsIgnoreCase("bvariables")) {
            exit("Missing bvariable declarations: " + o);
        }
        o = i.next();
        _alBVarsTemplate = (ArrayList<String>) ((ArrayList) o).clone();

        while (i.hasNext()) {
            o = i.next();
            if (!(o instanceof String)) {
                exit("Requires string for cpt head variable: " + o);
            }
            Object cpt = i.next();
            if (!(cpt instanceof ArrayList)) {
                exit("Decision diagram (...) must follow cpt: " + cpt);
            }
            _hmVar2cptTemplate.put((String) o, (ArrayList) cpt);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Graphical Model Template:\n================\n");
        sb.append("Boolean variables:     " + _alBVarsTemplate + "\n");
        sb.append("Continuous variables:  " + _alCVarsTemplate + "\n");
        sb.append("Continuous low range:  " + _hmCVar2MinValue + "\n");
        sb.append("Continuous high range: " + _hmCVar2MaxValue + "\n");
        sb.append("CPFs:\n");
        for (Map.Entry<String, ArrayList> e : _hmVar2cptTemplate.entrySet())
            sb.append(" - " + e.getKey() + ": " + e.getValue() + "\n");
        if (_alFactors != null) {
            sb.append("\nModel instantiation:\n");
            sb.append("All variables in factors: " + _hsVariables + "\n");
            sb.append("All variables in XADD: " + _context._alOrder + "\n");
            for (Factor f : _alFactors) {
                sb.append(" - " + f + "\n");
                //_context.getGraph(f._xadd).launchViewer();
            }
        }
        return sb.toString();
    }

    public void exit(String msg) {
        System.err.println(msg);
        new Exception().printStackTrace(System.err);
        System.exit(1);
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        //GraphicalModel gm = new GraphicalModel("./src/sve/test2.gm");
        GraphicalModel gm = new GraphicalModel("./src/sve/tracking.gm");
        //Query q = new Query("./src/sve/test.query");
        Query q = new Query("./src/sve/tracking.query.6");
        //System.out.println(gm);
        System.out.println(q);
        gm.instantiateGMTemplate(q._hmVar2Expansion);
        System.out.println(gm);
    }

}
