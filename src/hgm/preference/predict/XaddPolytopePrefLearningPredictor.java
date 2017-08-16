package hgm.preference.predict;

import hgm.asve.Pair;
import hgm.preference.Choice;
import hgm.preference.XaddBasedPreferenceLearning;
import hgm.preference.db.PreferenceDatabase;
import hgm.sampling.XaddSampler;
import hgm.sampling.SamplingFailureException;
import hgm.sampling.VarAssignment;
import xadd.XADD;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Created by Hadi Afshar.
 * Date: 2/02/14
 * Time: 5:32 AM
 */
public abstract class XaddPolytopePrefLearningPredictor implements PreferenceLearningPredictor {
    boolean DEBUG_MODE = true;
    private double indicatorNoise;
    private boolean reduceLP;
    private int numberOfSamples;
    private double relativeLeafValueBelowWhichRegionsAreTrimmed;
    private double epsilon;

    private List<Double[]> takenSamples;
    private int burnedSamples;

    public XaddPolytopePrefLearningPredictor(double indicatorNoise,
                                             boolean reduceLP,
                                             int numberOfSamples,
                                             double relativeLeafValueBelowWhichRegionsAreTrimmed,
                                             double epsilon,
                                             int burnedSamples) {
        this.indicatorNoise = indicatorNoise;
        this.reduceLP = reduceLP;
        this.numberOfSamples = numberOfSamples;
        this.relativeLeafValueBelowWhichRegionsAreTrimmed = relativeLeafValueBelowWhichRegionsAreTrimmed;
        this.epsilon = epsilon;
        this.burnedSamples =burnedSamples;
    }

    @Override
    public Info learnToPredict(PreferenceDatabase trainingDatabase) {
        Info info = new Info();

        XADD context = new XADD();
        XaddBasedPreferenceLearning learning = new XaddBasedPreferenceLearning(context, trainingDatabase, indicatorNoise, "w", epsilon);

        long time1start = System.currentTimeMillis();
        // Pr(W | R^{n+1})
        XADD.XADDNode posterior = learning.computePosteriorWeightVector(reduceLP, relativeLeafValueBelowWhichRegionsAreTrimmed);
        fixVarLimits(context, posterior, -XaddBasedPreferenceLearning.C, XaddBasedPreferenceLearning.C); //todo: do something better...

        info.add("#posteriorNodes", (double) posterior.collectNodes().size());

        long time2posteriorCalculated = System.currentTimeMillis();
        info.add(new Pair<String, Double>("T:posterior", (double) time2posteriorCalculated - time1start));

        //extra reduction phase.... long time3posteriorReduced = System.currentTimeMillis();

        XaddSampler sampler = makeNewSampler(context, posterior, learning.generateAWeightVectorHighlyProbablePosteriorly());

        takenSamples = new ArrayList<Double[]>(numberOfSamples);

        for (int i = 0; i < numberOfSamples; i++) {
            VarAssignment assign = sampler.sample();

            if (DEBUG_MODE) {
                Double eval = context.evaluate(context._hmNode2Int.get(posterior), assign.getBooleanVarAssign(), assign.getContinuousVarAssign());
                if (eval == null || eval <= 0.0) {
                    throw new RuntimeException("eval" + eval + "had to be > 0!");
                }
            }

            Double[] cAssign = assign.getContinuousVarAssignAsArray("w");
            takenSamples.add(cAssign);
        }
        
//        sampler.finish();
        
        long time5samplesTaken = System.currentTimeMillis();
        info.add(new Pair<String, Double>("T:sampling", (double) time5samplesTaken - time2posteriorCalculated));
        return info;
    }

    //sum_i a_i.b_i
    private double util(Double[] a, Double[] b) {
        double u = 0d;
        if (a.length != b.length) throw new RuntimeException("size mismatch");
        for (int i = 0; i < a.length; i++) {
            u += (a[i] * b[i]);
        }
        return u;
    }

    public static void fixVarLimits(XADD context, XADD.XADDNode root, double varMin, double varMax) {
        HashSet<String> vars = root.collectVars();
        for (String var : vars) {
            context._hmMinVal.put(var, varMin);
            context._hmMaxVal.put(var, varMax);
        }
    }

    public abstract XaddSampler makeNewSampler(XADD context, XADD.XADDNode posterior, VarAssignment assignment);

    @Override
    public Choice predictPreferenceChoice(Double[] a, Double[] b) {
        return predictPreferenceChoice(a, b, burnedSamples, takenSamples.size() - burnedSamples);  //note that 100 samples are burnt...
    }

    public Choice predictPreferenceChoice(Double[] a, Double[] b, int numberOfBurnedSamples, int numberOfSamplesTakenIntoAccount) {
        if (numberOfSamplesTakenIntoAccount + numberOfBurnedSamples > takenSamples.size()) throw new SamplingFailureException(
                "Out of bound exception: #Burned= " + numberOfBurnedSamples + "\t#effective samples= " + numberOfSamplesTakenIntoAccount + "\t exceeds: " + takenSamples.size());
        Choice predictedChoice;

        int timesAIsGreaterThanB = 0;
        int timesBIsGreaterThanA = 0;
        int timesAEqualsB = 0;

        for (int i = 0; i < numberOfSamplesTakenIntoAccount; i++) {
            Double[] sampledW = takenSamples.get(i + numberOfBurnedSamples); //I take the samples backward to increase the effect of sample burning
            double utilA = util(a, sampledW);
            double utilB = util(b, sampledW);
            if (utilA - utilB > 0) {
                timesAIsGreaterThanB++;
            } else if (utilB - utilA > 0) {
                timesBIsGreaterThanA++;
            } else timesAEqualsB++;
        }

        double maxCount = Math.max(timesAEqualsB, Math.max(timesAIsGreaterThanB, timesBIsGreaterThanA));
        if (maxCount == timesAIsGreaterThanB) predictedChoice = Choice.FIRST;
        else if (maxCount == timesBIsGreaterThanA) predictedChoice = Choice.SECOND;
        else predictedChoice = Choice.EQUAL;

        return predictedChoice;
    }

    @Override
    public double probabilityOfFirstItemBeingPreferredOverSecond(Double[] a, Double[] b) {
        return  probabilityOfFirstItemBeingPreferredOverSecond(a, b, burnedSamples, takenSamples.size() - burnedSamples);  //note that 100 samples are burnt...
    }

    public double probabilityOfFirstItemBeingPreferredOverSecond(Double[] a, Double[] b, int numberOfBurnedSamples, int numberOfSamplesTakenIntoAccount) {
        if (numberOfSamplesTakenIntoAccount + numberOfBurnedSamples > takenSamples.size()) throw new SamplingFailureException(
                "Out of bound exception: #Burned= " + numberOfBurnedSamples + "\t#effective samples= " + numberOfSamplesTakenIntoAccount + "\t exceeds: " + takenSamples.size());

        double n = 0;
        double sumProb = 0;

        for (int i = 0; i < numberOfSamplesTakenIntoAccount; i++) {
            n++;

            Double[] sampledW = takenSamples.get(i + numberOfBurnedSamples); //I take the samples backward to increase the effect of sample burning
            double utilA = util(a, sampledW);
            double utilB = util(b, sampledW);

            if (utilA == 0 && utilB==0) {
                System.err.println("both utils 0 in XADD.poly.P.L.Predict");
                sumProb += 0.5;
            } else {
                sumProb += (utilA/(utilA + utilB));
            }

        }

        return sumProb/n;
    }


}
