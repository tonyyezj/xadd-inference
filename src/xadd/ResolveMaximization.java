package xadd;
import xadd.ExprLib;
import xadd.ExprLib.ArithExpr;
import xadd.ExprLib.ArithOperation;
import xadd.ExprLib.CoefExprPair;
import xadd.ExprLib.CompExpr;
import xadd.ExprLib.CompOperation;
import xadd.ExprLib.DoubleExpr;
import xadd.XADD;
import xadd.XADD.Decision;
import xadd.XADD.ExprDec;

import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Supplier;

import static java.lang.String.format;

/**
 * Created by samuelkolb on 07/06/2017.
 *
 * @author Samuel Kolb, Tony Ye
 */

/* NOTE: Only works for *max* for now.
   Code needs to be modified to accommodate minimization as well. */
/* See XADD code for the current max operator for reference. */


public class ResolveMaximization {

	private class ResolveKey {
		final int rootId;
		final String variable;
		final ArithExpr optUb;
		final ArithExpr optLb;

		ResolveKey(int rootId, Variable variable, ArithExpr optUb, ArithExpr optLb) {
			this.rootId = rootId;
			this.variable = variable.getName();
			this.optUb = optUb;
			this.optLb = optLb;
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;

			ResolveKey that = (ResolveKey) o;

			if(rootId != that.rootId) return false;
			if(variable != null ? !variable.equals(that.variable) : that.variable != null) return false;
			if(optUb != null ? !optUb.equals(that.optUb) : that.optUb != null) return false;
			return optLb != null ? optLb.equals(that.optLb) : that.optLb == null;
		}

		@Override
		public int hashCode() {
			int result = rootId;
			result = 31 * result + (variable != null ? variable.hashCode() : 0);
			result = 31 * result + (optUb != null ? optUb.hashCode() : 0);
			result = 31 * result + (optLb != null ? optLb.hashCode() : 0);
			return result;
		}
	}

	//region Variables
	private HashMap<ResolveKey, Integer> resolveCache;
	private final boolean verbose;
	private final boolean reduce = false;
	private XADD context;
	//endregion

	//region Construction

	/**
	 * @param context	The XADD pool / context
	 */
	public ResolveMaximization(XADD context) {
		this(context, false);
	}

	/**
	 * @param context	The XADD pool / context
	 * @param verbose	Enable verbose printing if true
	 */
	public ResolveMaximization(XADD context, boolean verbose) {
		this.context = context;
		this.verbose = verbose;
	}

	//endregion

	//region Public methods

	/**
	 * Integrates the given variable from the given diagram
	 * @param rootId	The id of the root node of the diagram to integrate
	 * @param variable	The variable to eliminate
	 * @return	The integer node id of the resulting diagram
	 */
	public int maxOut(int rootId, Variable variable, double lb, double ub) {
		resolveCache = new HashMap<>();
		//return maxOut(rootId, variable, ExprLib.POS_INF, ExprLib.NEG_INF, "");
		return maxOut(rootId, variable, new DoubleExpr(lb), new DoubleExpr(ub), "");
	}

    /**
     * Log a message with the provided prefix and arguments, the message will be ignored if verbose is turned off
     * @param message   The format string message to log
     * @param prefix    The prefix to prepend
     * @param arguments The arguments provided for formatting
     */
	private void log(String message, String prefix, Object... arguments) {
		if(this.verbose) {
			System.out.println(prefix + format(message, arguments));
		}
	}

    /**
     * Recursively max out a variable from the given XADD
     * @param rootId    The integer node id of the XADD to eliminate the variable from
     * @param variable  The variable to eliminate
     * @param optUb The current upper bound for the variable
     * @param optLb The current lower bound for the variable
     * @param prefix    Logging prefix
     * @return  The integer node id corresponding to the resulting XADD
     */
	private int maxOut(int rootId, Variable variable, ArithExpr optUb, ArithExpr optLb, String prefix) {
		if(rootId == context.ZERO) {
			return context.ZERO;
		}

		ResolveKey key = new ResolveKey(rootId, variable, optUb, optLb);
		if(resolveCache.containsKey(key)) {
			log("Cache hit", prefix);
			// System.out.format("Cache hit %s %s % %s", rootId, variable, optLb, optUb);
			// System.out.println(rootId + " " + variable + " " + optLb + " " + optUb);
			return resolveCache.get(key);
		}

		XADD.XADDNode node = context.getNode(rootId);
		log("Resolve %s for var %s with ub %s and lb %s", prefix, node, variable, optUb, optLb);
		if(node instanceof XADD.XADDINode) {
			XADD.XADDINode internalNode = (XADD.XADDINode) node;
			HashSet<String> variables = new HashSet<>();
			//collect vars in the decision
			internalNode.getDecision().collectVars(variables);
			// does not have target Var ... go to children
			if(!variables.contains(variable.getName())) {
				int resolveLow = maxOut(internalNode._low, variable, optUb, optLb, prefix + "\t");
				int resolveHigh = maxOut(internalNode._high, variable, optUb, optLb, prefix + "\t");
				int resolved = context.getINodeCanon(internalNode._var, resolveLow, resolveHigh);
				log("Resolved did not contain: %s", prefix, context.getNode(resolved));
				resolveCache.put(key, resolved);
				return resolved;
			// contains target var
			} else {
				if(internalNode.getDecision() instanceof XADD.ExprDec) {
					ExprLib.CompExpr comparison = ((XADD.ExprDec) internalNode.getDecision())._expr;
					ExprLib.CoefExprPair pair = comparison._lhs.removeVarFromExpr(variable.getName());
					double coefficient = pair._coef;
					// var > -1/(abs(coef)) expr
					ArithExpr normalized = (ArithExpr) new ExprLib.OperExpr(ExprLib.ArithOperation.PROD,
							pair._expr, new ExprLib.DoubleExpr(1 / Math.abs(coefficient))).makeCanonical();
					
					final ArithExpr newBound; // contains the bound arithexpression

					int ubId, lbId;

					// means inequality is var < expr ... upper bound
					if(coefficient < 0) {
						log("UB branch is true", prefix);
						ubId = internalNode._high;
						lbId = internalNode._low;
						newBound = normalized;
						// var > - expr 。。。 lower bound if true
					} else if(coefficient > 0) {
						log("UB branch is false", prefix);
						ubId = internalNode._low; // = false corresponds to var < expr, upper bound
						lbId = internalNode._high; // true so lower bound
						ExprLib.OperExpr negated = new ExprLib.OperExpr(ExprLib.ArithOperation.MINUS, ExprLib.ZERO, normalized);
						newBound = (ArithExpr) negated.makeCanonical();
					} else {
						throw new IllegalStateException(format("Coefficient %s from expression %s should be non-zero",
								coefficient, comparison));
					}

					log(" Node %s, coefficient %.2f, bound: %s", prefix, comparison, coefficient, newBound);

					// f_u = (u_{new} \geq l) * \ite(u > u_{new}, br(x, h(f), u_{new}, l), br(x, h(f), u, l))$
					// f_l = (l_{new} \leq u) * \ite(l < l_{new}, br(x, l(f), u, l_{new}), br(x, l(f), u, l))$

					int ubConsistencyId, ubIte, lbConsistencyId, lbIte;

					// TODO pass_ub / pass/lb

					if(optLb != ExprLib.NEG_INF) {
						ubConsistencyId = comparisonToNodeId(ExprLib.CompOperation.GT_EQ, newBound, optLb);

						// case where it's less than the current lower bound
						Supplier<Integer> resolveFalseSupplier =
								() -> maxOut(lbId, variable, optUb, optLb, prefix + "\t");
						// case where the new bound is larger the lower bound (so take it)
						Supplier<Integer> resolveTrueSupplier =
								() -> maxOut(lbId, variable, optUb, newBound, prefix + "\t");
						// introduce new decision: if lbsofar <= newbound, then result is max with newBound as lb...
						XADD.Decision decision = getDecision(ExprLib.CompOperation.LT_EQ, optLb, newBound);
						// dec, ifTrue, ifFalse
						lbIte = simplifyIte(decision, resolveTrueSupplier, resolveFalseSupplier, prefix);
					} else {
						ubConsistencyId = context.getTermNode(ExprLib.ONE);
						lbIte = maxOut(lbId, variable, optUb, newBound, prefix + "\t");
					}

					if(optUb != ExprLib.POS_INF) {
						lbConsistencyId = comparisonToNodeId(ExprLib.CompOperation.LT_EQ, newBound, optUb);

						Supplier<Integer> resolveFalseSupplier =
								() -> maxOut(ubId, variable, optUb, optLb, prefix + "\t");
						Supplier<Integer> resolveTrueSupplier =
								() -> maxOut(ubId, variable, newBound, optLb, prefix + "\t");
						// currBound >= newbound, if true use newbound as upper bound
						XADD.Decision decision = getDecision(ExprLib.CompOperation.GT_EQ, optUb, newBound);
						ubIte = simplifyIte(decision, resolveTrueSupplier, resolveFalseSupplier, prefix);
					} else {
						lbConsistencyId = context.getTermNode(ExprLib.ONE);
						ubIte = maxOut(ubId, variable, newBound, optLb, prefix + "\t");
					}

					// Branches
					int ubBranch = context.apply(ubConsistencyId, ubIte, XADD.PROD);
					if(reduce) {
						ubBranch = context.reduceLP(ubBranch);
					}
					int lbBranch = context.apply(lbConsistencyId, lbIte, XADD.PROD);
					if(reduce) {
						lbBranch = context.reduceLP(lbBranch);
					}

					if(ubBranch == 1 && lbBranch == 1) {
						log("Ub consistency: %s >= %s", prefix, newBound, optLb);
						log("%d", "", comparisonToNodeId(ExprLib.CompOperation.GT_EQ,
								newBound, optLb));
						log("%d", "", comparisonToVarId(ExprLib.CompOperation.GT_EQ,
								newBound, optLb));
						log("Product ub: %d and %d, lb: %d and %d", prefix, ubConsistencyId, ubIte, lbConsistencyId, lbIte);
						log("Consistency node %s", prefix, context.getNode(ubConsistencyId));
					}

					int resolved = context.apply(ubBranch, lbBranch, XADD.MAX);
					resolved = context.reduceLP(resolved);

					log("Resolved summed: %s + %s = %s", prefix, context.getNode(ubBranch),
							context.getNode(lbBranch), context.getNode(resolved));
					resolveCache.put(key, resolved);
					return resolved;
				}
				else {
					int maxed = context.apply(internalNode._low, internalNode._high, XADD.MAX);
					maxed = context.reduceLP(maxed);
					resolveCache.put(key, maxed);
					return maxed;
				}
			}
		} else if(node instanceof XADD.XADDTNode) {
			XADD.XADDTNode terminalNode = (XADD.XADDTNode) node;
			log("compute max called!!", "");
			int resolved = computeMax(rootId, terminalNode._expr, variable, optUb, optLb);
			log("Terminal node integrated to return %s", prefix, context.getNode(resolved));
			resolveCache.put(key, resolved);
			return resolved;
		} else {
			throw new IllegalStateException(format("Unexpected subclass %s of XADDNode %s", node.getClass(), node));
		}
	}

    /**
     * Constructs an if-then-else XADD, where branches are only lazily evaluated if the decision is not a tautology
     * @param decision  The condition
     * @param ifTrue    A lazy node id for the true branch
     * @param ifFalse   A lazy node id for the false branch
     * @param prefix    Logging prefix
     * @return  The integer node id of the resulting XADD
     */
	private int simplifyIte(XADD.Decision decision, Supplier<Integer> ifTrue, Supplier<Integer> ifFalse, String prefix) {
		if(decision instanceof XADD.TautDec) {
			XADD.TautDec tautology = (XADD.TautDec) decision;
			if(tautology._bTautology) {
				log("%s is tautology, resolving only true branch", prefix, tautology);
				return ifTrue.get();
			} else {
				log("%s is inconsistency, resolving only false branch", prefix, tautology);
				return ifFalse.get();
			}
		} else {
			int varId = context.getVarIndex(decision, true);
			int resolveFalse = ifFalse.get();
			int resolveTrue = ifTrue.get();
			log("if %s then %s else %s", prefix, varId, resolveTrue, resolveFalse);
			return context.getINodeCanon(varId, resolveFalse, resolveTrue);
		}
	}

    /**
     * Maxes out the given variable from the expression
     * @param expr  The expression
     * @param variable  The variable to max out
     * @param optUb The upper bound of the variable
     * @param optLb The lower bound of the variable
     * @return  The integer node id representing the maxed out expression
     */
	private int computeMax(int node_id, ArithExpr expr, Variable variable, ArithExpr optUb, ArithExpr optLb) {
		if(expr.equals(ExprLib.ZERO)) {
			log("Return 0 for the integration of 0 for %s in [%s, %s]", "", variable, optLb, optUb);
			return context.getTermNode(ExprLib.ZERO);
		}
		
		if(variable.isBool()) {
			return context.getTermNode(expr);
		} else if(variable.isReal()) {

			String var = variable.getName();
			
			// TODO Fill in symbolic maximization
			HashSet<String> vars = new HashSet<String>();
			expr.collectVars(vars);
			if (!vars.contains(variable.getName())) {
				return node_id;
			}
			boolean _bIsMax = true;           
			ArithExpr evalLb = expr.substitute(mapTo(var, optLb));
			ArithExpr evalUb = expr.substitute(mapTo(var, optUb));
			int maxResult = context.apply(context.getTermNode(evalLb), context.getTermNode(evalUb), XADD.MAX);

            ArithExpr root = null;
            int highest_order = expr.determineHighestOrderOfVar(var);
            if (highest_order > 2) {
                log("Cannot currently handle expressions higher than order 2 in %s ", var + ": " + expr);
                System.exit(1);
            } else if (highest_order == 2) {
                ArithExpr first_derivative = expr.differentiateExpr(var);

                // Takes ArithExpr expr1 linear in var, returns (coef,expr2) where expr1 = coef*x + expr2
                // setting expr1 = coef*x + expr2 = 0 then x = -expr2/coef
                CoefExprPair p2 = first_derivative.removeVarFromExpr(var);

                root = (ArithExpr) (new ExprLib.OperExpr(ExprLib.ArithOperation.MINUS, ExprLib.ZERO, new ExprLib.OperExpr(ExprLib.ArithOperation.PROD, new ExprLib.DoubleExpr(
                        1d / p2._coef), p2._expr)).makeCanonical());
            }


            if (root != null) {

                int eval_root = context.substituteXADDforVarInArithExpr(expr, var, context.getTermNode(root));
                //if (VERBOSE_MIN_MAX) _log.println("root substitute: " + getString(eval_root));

                // Now incorporate constraints into int_eval, make result canonical
                //for (ArithExpr ub : upper_bound) {
                	CompExpr ce = new ExprLib.CompExpr(ExprLib.CompOperation.LT_EQ, root, optUb);
                    int ub_xadd = _bIsMax
                            ? context.getVarNode(context.new ExprDec(ce), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)  // +inf gets min'ed to eval_root
                            : context.getVarNode(context.new ExprDec(ce), Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY); // -inf gets max'ed to eval_root
                    // For discussion of following operation, see independent decisions modification stage below  
                    eval_root = context.apply(ub_xadd, eval_root, _bIsMax ? XADD.MIN : XADD.MAX); // NOTE: this is correct, it is not reversed
                //}
                //for (ArithExpr lb : lower_bound) {
                    CompExpr ce2 = new CompExpr(CompOperation.GT, root, optLb);
                    int lb_xadd = _bIsMax
                            ? context.getVarNode(context.new ExprDec(ce2), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)  // +inf gets min'ed to eval_root
                            : context.getVarNode(context.new ExprDec(ce2), Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY); // -inf gets max'ed to eval_root
                    // For discussion of following operation, see independent decisions modification stage below  
                    eval_root = context.apply(lb_xadd, eval_root, _bIsMax ? XADD.MIN : XADD.MAX); // NOTE: this is correct, it is not reved 
                //}
                //max_eval_root = reduceLinearize(max_eval_root); // Removed previously
                //max_eval_root = reduceLP(max_eval_root); // Result should be canonical

                //if (VERBOSE_MIN_MAX) _log.println("constrained root substitute: " + getString(eval_root));
                maxResult = context.apply(maxResult, eval_root, _bIsMax ? XADD.MAX : XADD.MIN); // handle min or max
                maxResult = context.reduceLinearize(maxResult);
                maxResult = context.reduceLP(maxResult); // Result should be canonical
                // if (VERBOSE_MIN_MAX)
                //     _log.println(_sOpName + " of constrained root sub and int_eval(LB/UB): " + getString(min_max_eval));
            }

			// lb < ub
            CompExpr ce = new CompExpr(CompOperation.GT, optUb, optLb);
            ExprDec ubGtLb = context.new ExprDec(ce);
            HashMap<Decision, Boolean> target_var_indep_decisions = new HashMap<Decision, Boolean>();
            target_var_indep_decisions.put(ubGtLb, Boolean.TRUE);
            for (HashMap.Entry<Decision, Boolean> me : target_var_indep_decisions.entrySet()) {
                double high_val = ((me.getValue() && _bIsMax) || (!me.getValue() && !_bIsMax)) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                double low_val = ((me.getValue() && _bIsMax) || (!me.getValue() && !_bIsMax)) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
                int indep_constraint = context.getVarNode(me.getKey(), low_val, high_val);
                // if (VERBOSE_MIN_MAX)
                //     _log.println("max_eval with decisions: " + me.getKey() + " [" + me.getValue() + "] -->\n" + getString(indep_constraint));
                // Note: need to make function -INF when constraints violated: min(f,(v -inf +inf)) = (v -inf f)
                maxResult = context.apply(indep_constraint, maxResult, _bIsMax ? XADD.MIN : XADD.MAX); // NOTE: this is correct, it is not reversed
            }

            maxResult = context.reduceLinearize(maxResult);
            maxResult = context.reduceLP(maxResult); // Result should be canonical


            // Optional: cache symbolic solution (without bounds filled in)
			// ArithExpr integrated = expr.integrateExpr(variable.getName());
			// ArithExpr substitutedUb = integrated.substitute(mapTo(variable.getName(), optUb));
			// ArithExpr substitutedLb = integrated.substitute(mapTo(variable.getName(), optLb));
			// ArithExpr result = ArithExpr.op(substitutedUb, substitutedLb, ExprLib.ArithOperation.MINUS);

			//result = (ArithExpr) result.makeCanonical();
			//log("Integrating %s for %s in [%s, %s] gives %s", "", expr, variable, optLb, optUb, result);
			//return context.getTermNode(result);
			return maxResult;
		} else {
			throw new IllegalArgumentException(format("Could not integrate term for variable %s", variable));
		}
	}

	private XADD.Decision getDecision(ExprLib.CompOperation op, ArithExpr lhs, ArithExpr rhs) {
		return context.new ExprDec(new ExprLib.CompExpr(op, lhs, rhs));
	}

	private int comparisonToVarId(ExprLib.CompOperation op, ArithExpr lhs, ArithExpr rhs) {
		ExprLib.CompExpr compExpr = new ExprLib.CompExpr(op, lhs, rhs);
		return context.getVarIndex(context.new ExprDec(compExpr), true);
	}

	private int booleanNodeId(int varId) {
		log("Building i-node if %d then %d else %d)", "", varId, context.getTermNode(ExprLib.ONE),
				context.getTermNode(ExprLib.ZERO));
		return context.getINode(varId, context.getTermNode(ExprLib.ZERO), context.getTermNode(ExprLib.ONE));
	}

	private int comparisonToNodeId(ExprLib.CompOperation op, ArithExpr lhs, ArithExpr rhs) {
		return booleanNodeId(comparisonToVarId(op, lhs, rhs));
	}

	private <K, V> HashMap<K, V> mapTo(K key, V value) {
		HashMap<K, V> map = new HashMap<>();
		map.put(key, value);
		return map;
	}
	//endregion
}