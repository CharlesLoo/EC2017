

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ec2017.ga.general.*;
import ttp.TTPInstance;
import ttp.TTPSolution;

/**
 * The main entry point for our program.
 * @author pat
 *
 */
public class TTPSolver
{
	// Setup our thread pool.
	static ExecutorService _executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

	public static void main(String[] args) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException
	{
		// Load the parameters from config.properties
		File configFile = new File("config.properties");
		Properties prop = new Properties();
		prop.load(new FileReader(configFile));

		// For each operator, dynamically load and instantiate the class we've been asked to use

		// There's no nop crossover operator, so if one hasn't been configured we default to null (which has the same effect)
		CrossOverOperator crossover = null;
		// We don't want to make people put fully-qualified classnames in the config file, so we add that bit here
		String crossoverClassName = "ec2017.ga.general.variation." + prop.getProperty("crossover");
		if (crossoverClassName != null) {
			// If a crossover operator has been configured, we load the class and call the no-argument constructor
			Class<? extends CrossOverOperator> crossoverClass =
					(Class<? extends CrossOverOperator>) Class.forName(crossoverClassName);
			crossover = crossoverClass.getConstructor().newInstance();
		}

		// Same here for the mutation operator, except we default to MutateNop instead of null
		String mutationClassName = "ec2017.ga.general.variation." + prop.getProperty("mutate", "MutateNop");
		Class<? extends MutateOperator> mutateClass =
				(Class<? extends MutateOperator>) Class.forName(mutationClassName);
		MutateOperator mutator = mutateClass.getConstructor().newInstance();

		// Same here for the parent selection operator, defaulting to NoParentSelectionMethod
		String parentSelectionClassName = "ec2017.ga.general.selection." + prop.getProperty("parent-selection", "NoParentSelectionMethod");
		Class<? extends ParentSelectionMethod> parentSelectionClass =
				(Class<? extends ParentSelectionMethod>) Class.forName(parentSelectionClassName);
		ParentSelectionMethod parentSelector = parentSelectionClass.getConstructor().newInstance();

		// Same here for the parent selection operator, defaulting to NoSurvivorSelectionMethod
		String survivorSelectionClassName = "ec2017.ga.general.selection." + prop.getProperty("survivor-selection", "NoSurvivorSelectionMethod");
		Class<? extends SurvivorSelectionMethod> survivorSelectionClass =
				(Class<? extends SurvivorSelectionMethod>) Class.forName(survivorSelectionClassName);
		SurvivorSelectionMethod survivorSelector = survivorSelectionClass.getConstructor().newInstance();

		// Load the population size, number of generations, and how many times to run the EA,
		// with reasonable defaults
		int population = new Integer(prop.getProperty("population", "50"));
		int genetations = new Integer(prop.getProperty("generations", "2000"));
		int runs = new Integer(prop.getProperty("runs", "1"));

		// Create the algorithm
		Algorithm algorithm = new Algorithm(
				crossover,
				mutator,
				parentSelector,
				survivorSelector);

		// Run it
		runTests(algorithm, population, genetations, runs);

        System.out.println("************* Done *************");

	}

	private static void runTests(
			Algorithm algorithm,
			int populationSize,
			int generations,
			int runs)
	{
		StringBuilder resultsLog = new StringBuilder();
		StringBuilder generationLog = new StringBuilder();

		File inputFolder = new File("TTP_data");
		for(File inFile : inputFolder.listFiles())
		{
			resultsLog.append("Input: ");
			resultsLog.append(inFile.getName());
			resultsLog.append(System.lineSeparator());
			resultsLog.append("Costs:");
			resultsLog.append(System.lineSeparator());

			System.out.println("Reading..." + inFile.getName());

			double total = 0;
			double best = Double.MIN_VALUE;
			TTPSolution optimal = null;

			TTPInstance ttp = new TTPInstance(inFile);

			double[] values = new double[runs];

			for (int i = 0; i < runs; i++)
			{
				long starttime = System.currentTimeMillis();

				Population population =
					new Population(ttp, populationSize, algorithm);

				generationLog.append(inFile.getName());
				generationLog.append(',');

				// Do our evolution
				for (int j = 0; j < generations; j++)
				{
					population.evolve();
					if (j == 1 || j % 500 == 0)
					{
						TTPSolution solution = population.getFittest();
						generationLog.append(solution.ob);
						generationLog.append(',');
					}
				}

				generationLog.append(System.lineSeparator());

				TTPSolution bestSolution = population.getFittest();
				if(bestSolution.ob > best)
				{
					optimal = bestSolution;
					best = bestSolution.ob;
				}

				total += bestSolution.ob;
				values[i] = bestSolution.ob;
				resultsLog.append(bestSolution.ob);
				resultsLog.append(',');

				long endtime = System.currentTimeMillis();
				System.out.println(inFile.getName() + "-- run [" + i + "] -- " + ((endtime-starttime)/1000.0) + " seconds -- " + algorithm.toString());
			}

			double mean = total / runs;

			// Work out standard deviation.
			double stdDev = 0;
			for(int k = 0; k < values.length; k++)
			{
				values[k] = values[k] - mean;
				values[k] *= values[k];
				stdDev += values[k];
			}
			stdDev /= runs;
			stdDev = Math.sqrt(stdDev);

			resultsLog.append(System.lineSeparator());
			resultsLog.append("Mean cost: ");
			resultsLog.append(mean);
			resultsLog.append(System.lineSeparator());
			resultsLog.append("Std Deviation: ");
			resultsLog.append(stdDev);
			resultsLog.append(System.lineSeparator());

			System.out.println(inFile.getPath());
			System.out.println(inFile.getPath() + " :: " + algorithm.toString() + " mean: " + mean + '\n');
		}

		System.out.println(resultsLog.toString());

		try
		{
			StringBuilder fileName = new StringBuilder();
			fileName.append(algorithm.toString());
			fileName.append(",pop_");
			fileName.append(populationSize);
			fileName.append(",gen_");
			fileName.append(generations);
			fileName.append(",runs_");
			fileName.append(runs);

			BufferedWriter bw = new BufferedWriter(new FileWriter(new File("output/" + fileName.toString() + ".txt")));
			bw.write(resultsLog.toString());
			bw.close();

			bw = new BufferedWriter(new FileWriter(new File("output/" + fileName.toString()+ "-generations.csv")));
			bw.write(generationLog.toString());
			bw.close();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}