package hgm.asve.cnsrv.infer;

import hgm.asve.cnsrv.FactorSet;
import hgm.asve.cnsrv.factor.Factor;
import hgm.asve.cnsrv.factory.ModelBasedXaddFactorFactory;
import hgm.asve.cnsrv.gm.FBQuery;

import java.util.*;

/**
 * Created by Hadi Afshar.
 * Date: 19/09/13
 * Time: 4:02 PM
 * <p/>
 * It is lazy since multiplication is done as late as possible
 */
@Deprecated
public class LazyApproxSveInferenceEngine {
    public static boolean NORMALIZE_RESULT = true;

    private int _numberOfFactorsLeadingToJointFactorApproximation;
    private ModelBasedXaddFactorFactory factory;
    private Records _records;

    public LazyApproxSveInferenceEngine(ModelBasedXaddFactorFactory factory
            , int numberOfFactorsLeadingToJointFactorApproximation //todo, instead of num. of factors, if approx. num. of leaves (of a set of factors) is used, it might be better
    ) {

        this.factory = factory;
        _numberOfFactorsLeadingToJointFactorApproximation = numberOfFactorsLeadingToJointFactorApproximation;

        _records = new Records("Lazy approximate SVE");
//        _records.set("#factors.leading.to.joint.factor.approximation", ""+ numberOfFactorsLeadingToJointFactorApproximation);

    }

    //NOTE: specific order can cause bug, so it is a hack for testing only:
    Factor inferHack(List<String> varOrder, List<String> nonRemovableVariables) {
        Map<String, Integer> varScoreMap = new HashMap<String, Integer>();
        for (int i = 0; i < varOrder.size(); i++) {
            varScoreMap.put(varOrder.get(i), i + 1);
        }
        return infer(varScoreMap, nonRemovableVariables);
    }


    /**
     * @return inference (of query factors) s.t.:
     *         1. The children are processed after parents.
     *         2. Only query/evidence factors and their ancestors are taken into account.
     *         3. Factor multiplication (and approximation) is performed lazily (i.e. as late as possible)
     */
    public Factor infer() {
        FBQuery query = factory.getQuery();
        _records.set("query.variables", query.getQueryVariables().toString());
        _records.set("query.continuous.instantiated.evidence", query.getContinuousInstantiatedEvidence().toString());
        _records.set("query.boolean.instantiated.evidence", query.getBooleanInstantiatedEvidence().toString());

        //topologically score the graph:
        List<String> nonRemovableVariables = new ArrayList<String>();
        nonRemovableVariables.addAll(query.getQueryVariables());
        nonRemovableVariables.addAll(query.getNonInstantiatedEvidenceVariables());
        nonRemovableVariables.addAll(query.fetchInstantiatedEvidenceVariables()); //although instantiated variables are omitted, there corresponding factor is not.
        _records.set("non.removable.variables", nonRemovableVariables.toString());

        Map<String, Integer> varScoreMap = new HashMap<String, Integer>();
        for (String q : nonRemovableVariables) {
            topologicallyScoreSelfAndAncestors(q, varScoreMap);
        }
        _records.set("all.variables.taken.into.account", varScoreMap.keySet().toString());

        return infer(varScoreMap, nonRemovableVariables);
    }

    private Factor infer(Map<String, Integer> varScoreMap, List<String> nonRemovableVariables) {

        //convert varScoreMap to factorScoreMap:
        Map<Factor, Integer> factorScoreMap = new HashMap<Factor, Integer>();
        for (String v : varScoreMap.keySet()) {
            factorScoreMap.put(factory.getAssociatedInstantiatedFactor(v), varScoreMap.get(v));
        }

        FactorSet unprocessedFactors = new FactorSet(factorScoreMap.keySet()); //I did not use the keySet directly fearing maybe in future I'll need it!

        Map<Integer, List<Factor>> scoreFactorsMap = getScoreFactorsMap(factorScoreMap);

        //process this data structure:
        List<Integer> scores = new ArrayList<Integer>(scoreFactorsMap.keySet());
        Collections.sort(scores);
        // In our terminology, a "(factor) joint set" is a set of factors that if multiplied together form a joint distribution.
        Set<FactorSet> collectionOfJointFactorSets = new HashSet<FactorSet>();

        for (Integer score : scores) {
            List<Factor> sameScoreFactors = scoreFactorsMap.get(score);
            while (sameScoreFactors.size() > 0) {

                //1. Find a new candidate factor to be a seed of a new set of joints:
                Factor chosenF = heuristicallyChooseBestFactor(sameScoreFactors);
                _records.desiredVariableEliminationOrder.add(factory.getAssociatedVariable(chosenF));

                sameScoreFactors.remove(chosenF);
                unprocessedFactors.remove(chosenF);
                FactorSet newJointFactorSet = new FactorSet(); //todo do I need a set or should I directly multiply the factors? A. See TODO comments below...
                newJointFactorSet.add(chosenF);

                //2. Transfer to it all members of any joint-set that contains any of its parent variables:
                // E.g. if parents(X)={A,B} (i.e. f(X,A,B) where f is the factor associated with variable X) then:
                // adding f(X,A,B) to { {f1(A,C,D),f2(C,E)}, {f3(G,H),f4(I)} }and performing step 2 ends in:
                // { {f3(G,H),f4(I)}, {f(X,A,B),f1(A,C,D),f2(C,E)} }
                Set<String> chosenFactorVars = chosenF.getScopeVars();//model.get.getParents(chosenF);
                for (Iterator<FactorSet> jointSetIterator = collectionOfJointFactorSets.iterator();
                     jointSetIterator.hasNext(); ) {
                    FactorSet jointSet = jointSetIterator.next();
                    Set<String> jointSetScopeVars = jointSet.getScopeVars();
                    if (!Collections.disjoint(jointSetScopeVars, chosenFactorVars)) {
                        newJointFactorSet.addAll(jointSet);
                        //remove the previous set:
                        jointSetIterator.remove(); // this is a safe way to "setOfJointFactorSets.remove(jointSet)" in iteration-loop
                    }
                }

                //3. In case there are variables exclusively used in the newly made "set of joint factors"
                // (i.e. not used in unprocessed factors),
                // multiply them and marginalize out the common variable (of course not if it is in query).
                // The whole concept of "set of joint factors" is to perform multiplication lazily hoping that
                // by variable elimination, some redundant multiplications can be prevented.
                Set<String> varScopeOfUnprocessedFactors = unprocessedFactors.getScopeVars();
                Set<String> removableVarsExclusivelyUsedInNewJointFactorSet = newJointFactorSet.getScopeVars();
                removableVarsExclusivelyUsedInNewJointFactorSet.removeAll(varScopeOfUnprocessedFactors);
                removableVarsExclusivelyUsedInNewJointFactorSet.removeAll(nonRemovableVariables);

                if (!removableVarsExclusivelyUsedInNewJointFactorSet.isEmpty()) {
                    Factor joint = factory.approximateMultiply(newJointFactorSet);  //todo why do not I do multiplication anyway?
                    //TODO IMPORTANT: OK I got it, you do not multiply hopping that you find a factor that is not encountererd in all elements of the factor set. In this case, you HAVE TO use the traditional SVE on the factor set and multiplying factors before marginalization is STUPID!!!
                    for (String varToMarginalize : removableVarsExclusivelyUsedInNewJointFactorSet) {
                        joint = factory.marginalize(joint, varToMarginalize);
                        System.out.println("f4. factory.getContext()._alBooleanVars = " + factory.getContext()._alBooleanVars);
                        _records.variablesActuallyMarginalized.add(varToMarginalize);
                    }
                    newJointFactorSet.clear();
                    newJointFactorSet.add(joint);
                }

                String preApproxFactorRecord = _records.factorSetRecordStr(newJointFactorSet, false);

                //4. If necessary, simplify the new joint factor set:
                if (approximationIsNecessary(newJointFactorSet)) {
                    Factor approxFactor = factory.approximateMultiply(newJointFactorSet);
//                    _factory.visualize1DFactor(multFactor, "mulitFactor");
//                    Factor approxFactor = _factory.approximate(multFactor/*, _approximationMassThreshold, _approximationVolumeThreshold*/);
//                    _factory.visualize1DFactor(approxFactor, "approx");
                    newJointFactorSet = new FactorSet(Arrays.asList(approxFactor));
                }

                String postApproxFactorRecord = _records.factorSetRecordStr(newJointFactorSet, true);
                _records.recordFactorSetApproximation(preApproxFactorRecord, postApproxFactorRecord);

                //5. Add the new joint factor set to the relevant set:
                collectionOfJointFactorSets.add(newJointFactorSet);

                Set<Factor> factorsInUse = new HashSet<Factor>(getAllFactors(collectionOfJointFactorSets));
                factorsInUse.addAll(factorScoreMap.keySet());
                factory.flushFactorsExcept(factorsInUse);
            } //end while
//            System.out.println("After score:= " + score + ", collection: " + collectionOfJointFactorSets);
        }

        // Make the final joint factor.
        // Nothing should be remained to marginalize out:
        FactorSet allRemainedFactors = new FactorSet();
        //The scope of each factor set of each collection should only contain query variables and collections should be disjoint (in any sense).
        for (FactorSet jointFactorSet : collectionOfJointFactorSets) {
            allRemainedFactors.addAll(jointFactorSet);
        }
        Factor multipliedRemainedFactors = factory.approximateMultiply(allRemainedFactors);

//        _factory.getVisualizer().visualizeFactor(multipliedRemainedFactors, ("Last step before normalization"));

        _records.recordFactor(multipliedRemainedFactors);


        Factor finalResult;
        if (NORMALIZE_RESULT) {
            finalResult = factory.normalize(multipliedRemainedFactors);
        } else {
            System.err.println("Warning: final normalization is not performed in Approx SVE");
            finalResult = multipliedRemainedFactors;
        }

//        _factory.getVisualizer().visualizeFactor(finalResult, ("normalized final:"));

        _records.recordFinalResult(finalResult);

        factory.makePermanent(Arrays.asList(finalResult));
        factory.flushFactorsExcept(Collections.EMPTY_LIST);
        return finalResult;
    }

    private Set<Factor> getAllFactors(Set<FactorSet> collectionOfJointFactorSets) {
        Set<Factor> allFactors = new HashSet<Factor>();
        for (FactorSet factorSet : collectionOfJointFactorSets) {
            allFactors.addAll(factorSet);
        }
        return allFactors;
    }

    private boolean approximationIsNecessary(FactorSet factorSet) {
        //TODO what is the good heuristic for approximation?
        return factorSet.size() >= _numberOfFactorsLeadingToJointFactorApproximation;
    }

    private Factor heuristicallyChooseBestFactor(List<Factor> factors) {
        //todo definitely needs to be re-written. NOW DUMMY:
        if (factors.isEmpty()) throw new RuntimeException("what?");
        return factors.get(0);
    }


    private Map<Integer, List<Factor>> getScoreFactorsMap(Map<Factor, Integer> factorScoreMap) {
        Map<Integer, List<Factor>> m = new HashMap<Integer, List<Factor>>();
        for (Factor f : factorScoreMap.keySet()) {
            Integer s = factorScoreMap.get(f);
            List<Factor> fs = m.get(s);
            if (fs == null) {
                fs = new ArrayList<Factor>();
                m.put(s, fs);
            }
            if (fs.contains(f)) {
                throw new RuntimeException("I do not know what has happened!");
            }
            fs.add(f);
        }
        return m;
    }

    private int topologicallyScoreSelfAndAncestors(String var, Map<String, Integer> variableScoreMap) {
        Integer currentScore = variableScoreMap.get(var);
        if (currentScore == null) {
            currentScore = 0;
        }

        int maxParentScore = 0;
        Set<String> parents = factory.getParents(var);
        for (String parent : parents) {
            maxParentScore = Math.max(maxParentScore, topologicallyScoreSelfAndAncestors(parent, variableScoreMap));
        }
        currentScore = Math.max(currentScore, maxParentScore + 1);
        variableScoreMap.put(var, currentScore);
        return currentScore;
    }

    public Records getRecords() {
        return _records;
    }
}
