package bucketelim;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

public class AMPLRunner {
	public static void main(String[] args) throws IOException {
		Process process;
		try {
			process = new ProcessBuilder("C:\\Program Files\\AMPL\\ampl.exe", "include 'C:\\Users\\Tony\\Documents\\AMPL\\testrun.run';").start();
			InputStream is = process.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			String line;

			System.out.printf("Output of running %s is:", Arrays.toString(args));

			while ((line = br.readLine()) != null) {
			  System.out.println(line);
			}				
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
