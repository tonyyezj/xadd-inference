package hgm.poly.sampling;

import hgm.sampling.SamplingFailureException;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Arrays;

/**
 * Created by Hadi Afshar.
 * Date: 12/03/14
 * Time: 8:26 AM
 */
public class SamplingUtils {
    public static void save2DSamples(SamplerInterface sampler, int numSamples, String fileName) throws FileNotFoundException {
        PrintStream ps;

        ps = new PrintStream(new FileOutputStream(fileName + ".txt"));

        for (int i = 0; i < numSamples; i++) {
            try{
            Double[] sample = sampler.reusableSample();
            System.out.println("sample = " + Arrays.toString(sample));
            ps.println(sample[0] + "\t" + sample[1]);
            }catch (SamplingFailureException e) {
                e.printStackTrace();
            }

        }

        ps.close();
    }

    public static void saveSamples(SamplerInterface sampler, int numSamples, String outputFileName) throws FileNotFoundException {
        PrintStream ps;

        ps = new PrintStream(new FileOutputStream(outputFileName));

        for (int i = 0; i < numSamples; i++) {
            Double[] sample = sampler.reusableSample();
//            System.out.println("Arrays.toString(sample) = " + Arrays.toString(sample));

            StringBuilder sb = new StringBuilder();
            for (Double sampledVar : sample) {
                sb.append(sampledVar + "\t");
            }
            ps.println(sb.toString());
//            System.out.println("sample = " + Arrays.toString(sample));
        }

        ps.close();
    }
}
