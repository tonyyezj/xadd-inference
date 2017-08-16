package hgm.poly.bayesian;

import hgm.asve.cnsrv.approxator.sampler.GridSampler;
import hgm.poly.PiecewiseExpression;
import hgm.poly.integral.OneDimFunction;
import hgm.poly.pref.FatalSamplingException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Hadi Afshar.
 * Date: 11/03/14
 * Time: 9:05 PM
 */
public class FullGibbsGeneralBayesianSampler extends AbstractGeneralBayesianGibbsSampler {

   /* public static FullGibbsGeneralBayesianSampler makeSampler(GeneralBayesianPosteriorHandler gph, double minForAllVars, double maxForAllVars, Double[] reusableInitialSample) {
        int varNum = gph.getPolynomialFactory().getAllVars().length;
        double[] cVarMins = new double[varNum];
        double[] cVarMaxes = new double[varNum];
        Arrays.fill(cVarMins, minForAllVars);
        Arrays.fill(cVarMaxes, maxForAllVars);
        return new FullGibbsGeneralBayesianSampler(gph, cVarMins, cVarMaxes, reusableInitialSample);
    }*/

    public FullGibbsGeneralBayesianSampler(GeneralBayesianPosteriorHandler gph/*, double[] cVarMins, double[] cVarMaxes*/, Double[] reusableInitialSample) {
        super(gph, /*cVarMins, cVarMaxes,*/ reusableInitialSample);
    }

    @Override
    protected void sampleSingleContinuousVar(/*String varToBeSampled,*/
                                             int varIndexToBeSampled,
                                             Double[] reusableVarAssign) throws FatalSamplingException { //todo: only var-index should be enough

        //todo: important: it is wrong to sample from all variables in the factory.... One should only sample from the variables in the cp
//        List<Boolean> gateMask = gph.adjustedReusableGateActivationMask(reusableVarAssign);
//        if (gateMask.size()!= gph.numberOfConstraints()) throw new RuntimeException();
//        int n = gph.numberOfLikelihoods();

        List<Integer> numCasesInLikelihoods = gph.getNumCasesInAllLikelihoods();
        int n = numCasesInLikelihoods.size();
        double[] counterMins = new double[n];
        Arrays.fill(counterMins, 0d);
        double[] counterMaxes = new double[n];
        for (int i = 0; i < n; i++) {
            counterMaxes[i] = numCasesInLikelihoods.get(i)-1; //since counter in inclusive...
        }
        double[] stepSizes = new double[n];
        Arrays.fill(stepSizes, 1);
        GridSampler gs = new GridSampler(
                counterMins, //new double[]{0d, 0d, 0d},
                counterMaxes, //new double[]{2, 1, 2},
                stepSizes); //new double[]{1, 1, 1});

        Iterator<double[]> sampleIterator = gs.getSampleIterator();
        final List<OneDimFunction> polyCDFs = new ArrayList<OneDimFunction>();
        List<Integer> gateMask;
        while (sampleIterator.hasNext()) {
            double[] next = sampleIterator.next();
//            System.out.println("next = " + Arrays.toString(next));
            gateMask = doubleArray2IntList(next);
            PiecewiseExpression pp = gph.makeActivatedSubFunction(gateMask);
//            polyCDFs.add(makeCumulativeDistributionFunction(polytope, varToBeSampled, reusableVarAssign));
           makeAndAddCumulativeDistributionFunctionsToList(pp, varIndexToBeSampled, reusableVarAssign, polyCDFs);
        }


        //at least one polytope CDF should be non-ZERO otherwise something has gone wrong....

//        boolean aNonZeroPolytopeFuncExists = false;
//        for (OneDimFunction polyCDF : polyCDFs) {
//            if (!polyCDF.equals(OneDimFunction.ZERO_1D_FUNCTION)) {
//                aNonZeroPolytopeFuncExists = true;
//                break;
//            }
//        }
//        if (!aNonZeroPolytopeFuncExists) throw new FatalSamplingException("all regions are zero");
        if (polyCDFs.isEmpty()) throw new FatalSamplingException("all regions are zero");


        OneDimFunction varCDF = new OneDimFunction() {
            @Override
            public double eval(double var) {

                double result = 0d;
                for (OneDimFunction polyCDF : polyCDFs) {
                    result += polyCDF.eval(var);
                }

              return result;
            }
        };

//        if (DEBUG) {
//            FunctionVisualizer.visualize(varCDF, -50, 50, 0.1, "+-CDF");
//        }

        double maxVarValue = cVarMaxes[varIndexToBeSampled];
        double minVarValue = cVarMins[varIndexToBeSampled];

        double s = takeSampleFrom1DFunc(varCDF, minVarValue, maxVarValue);

        // here the sample is stored....
        reusableVarAssign[varIndexToBeSampled] = s;
    }

    private List<Integer> doubleArray2IntList(double[] arr) {
        List<Integer> list = new ArrayList<Integer>(arr.length);
        for (double d : arr) {
            Integer i = (int) d;
            if (i != d) throw new RuntimeException("non-integer: " + d);
            list.add(i);
        }
        return list;
    }
}