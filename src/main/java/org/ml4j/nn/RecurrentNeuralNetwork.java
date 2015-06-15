package org.ml4j.nn;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.jblas.DoubleMatrix;
import org.ml4j.nn.activationfunctions.SigmoidActivationFunction;
import org.ml4j.nn.costfunctions.CostFunction;
import org.ml4j.nn.util.NeuralNetworkUtils;

/**
 * A supervised FeedForwardNeuralNetwork which predicts labels from input features and from context retained from previous inputs
 * 
 * Consists of a RecurrentLayer and subsequent FeedForwardLayer.
 * 
 * Is trained by providing sequences of inputs and sequences of outputs.  Input and Output training sequences are grouped into 
 * DoubleMatrix instances whose rows represent sequences of a specific length. 
 * 
 * @author Michael Lavelle
 *
 */
public class RecurrentNeuralNetwork extends BaseFeedForwardNeuralNetwork<DirectedLayer<?>, RecurrentNeuralNetwork> {

	private double trainingRate;
	private int maxSequenceLength;

	public RecurrentNeuralNetwork(RecurrentLayer recurrentLayer, FeedForwardLayer feedForwardLayer, int maxSequenceLength,double trainingRate) {
		super(new DirectedLayer<?>[] { recurrentLayer, feedForwardLayer });
		this.trainingRate = trainingRate;
		this.maxSequenceLength = maxSequenceLength;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected int[][] getRetrainableTopologies() {
		int count = 0;
		for (DirectedLayer<?> layer : layers) {
			if (layer.isRetrainable())
				count++;
		}
		int[][] topologies = new int[count][2];
		int ind = 0;
		for (DirectedLayer<?> layer : layers) {
			if (layer.isRetrainable()) {
				if (layer instanceof RecurrentLayer) {
					topologies[ind] = new int[] { layer.getOutputNeuronCount(),
							layer.getOutputNeuronCount() + layer.getInputNeuronCount() + (layer.hasBiasUnit() ? 1 : 0) };

				} else {
					topologies[ind] = new int[] { layer.getOutputNeuronCount(),
							layer.getInputNeuronCount() + (layer.hasBiasUnit() ? 1 : 0) };
				}
				ind++;

			}

		}

		return topologies;
	}

	public void trainOnSequences(DoubleMatrix[] inputSequencesBySequenceLength, DoubleMatrix[] desiredOutputSequencesBySequenceLength, double[] lambdas, int max_iter) {

		if (!isContainingRetrainableLayers()) {
			throw new IllegalStateException(
					"NeuralNetwork must contain at least one (re)trainable layer before calling train method");
		}

		// This clones the NeuralNetwork, minimises the thetas, and returns
		// optimal thetas
		DoubleMatrix newThetas = getMinimisingThetasForRetrainableLayers(inputSequencesBySequenceLength, desiredOutputSequencesBySequenceLength,
				getClonedRetrainableThetas(), lambdas, getDefaultCostFunction(), max_iter);
		updateThetasForRetrainableLayers(newThetas, false);
	}
	
	private ForwardPropagation createForwardPropagation(FeedForwardNeuralNetwork timeUnfoldedNetwork,int inputCountWithBias,int sequenceLength,DoubleMatrix inputSequence,DoubleMatrix initialHiddenActivations)
	{
		List<NeuralNetworkLayerActivation<?>> timeUnfoldedActivations = new ArrayList<NeuralNetworkLayerActivation<?>>();

		List<NeuralNetworkLayerActivation<?>> hiddenActivationsForAllTimeSteps = new ArrayList<NeuralNetworkLayerActivation<?>>();

		DoubleMatrix previousHiddenActivations = initialHiddenActivations;

		DoubleMatrix outerActivation = null;

		// For each element of our sequence
		for (int h = 0; h < sequenceLength; h++) {

			// Get the sequence element
			DoubleMatrix sequenceInputs = inputSequence.getColumn(h);

			// Propagate sequence element through the entire original recurrent network
			ForwardPropagation sequenceProps = forwardPropagate(sequenceInputs);
			
			// Construct an activation of the relevant hidden layer in our time-unfolded network for this time-step
			
			DoubleMatrix hinWithIntercept = DoubleMatrix.concatHorizontally(DoubleMatrix.ones(1, 1),
					DoubleMatrix.concatHorizontally(sequenceInputs, previousHiddenActivations));
			DoubleMatrix hz = hinWithIntercept.mmul(timeUnfoldedNetwork.getLayers().get(h).getClonedThetas().transpose());
			DoubleMatrix hiddenActivations = sequenceProps.getActivations().get(0).getOutputActivations();
			
			
			NeuralNetworkLayerActivation<?> hiddenActivationForTimeStep = new NeuralNetworkLayerActivation<DirectedLayer<?>>(timeUnfoldedNetwork
					.getLayers().get(h), hinWithIntercept, hz, hiddenActivations);
			
			outerActivation = sequenceProps.getActivations().get(1).getOutputActivations();

			hiddenActivationsForAllTimeSteps.add(hiddenActivationForTimeStep);

			previousHiddenActivations = hiddenActivations;

		}

		
		// Construct and activation for the outer layer in our time-unfolded network.
		
		DoubleMatrix finalOut = outerActivation;
		DoubleMatrix finalIn = DoubleMatrix.concatHorizontally(
				DoubleMatrix.concatHorizontally(DoubleMatrix.ones(1, 1), new DoubleMatrix(1, inputCountWithBias)),
				previousHiddenActivations);

		DoubleMatrix finalZ = finalIn.mmul(timeUnfoldedNetwork.getOuterLayer().getClonedThetas().transpose());
		NeuralNetworkLayerActivation<?> finalActivation = new NeuralNetworkLayerActivation<DirectedLayer<?>>(
				timeUnfoldedNetwork.getOuterLayer(), finalIn, finalZ, finalOut);

		// Each forward propagation contains a hidden-layer activation for each timestep, and a final-layer activation
		timeUnfoldedActivations.addAll(hiddenActivationsForAllTimeSteps);
		timeUnfoldedActivations.add(finalActivation);

		return new ForwardPropagation(timeUnfoldedActivations.get(timeUnfoldedActivations.size() - 1).getOutputActivations(), timeUnfoldedActivations);
	}

	private FeedForwardNeuralNetwork createUnrolledNeuralNetwork(int sequenceLength, int inputCountWithBias,
			int hiddenCount, int outputCount, Vector<DoubleMatrix> initialRetrainableThetas) {
		List<FeedForwardLayer> layers = new ArrayList<FeedForwardLayer>();
		for (int h = 0; h < sequenceLength; h++) {

			DoubleMatrix thetas1 = new DoubleMatrix(inputCountWithBias + hiddenCount, inputCountWithBias + hiddenCount);
			DoubleMatrix t1 = initialRetrainableThetas.get(0);
			for (int r1 = 0; r1 < t1.getRows(); r1++) {
				for (int c1 = 0; c1 < t1.getColumns(); c1++) {
					thetas1.put(r1 + inputCountWithBias, c1, t1.get(r1, c1));
				}
			}

			FeedForwardLayer hiddenLayer = new FeedForwardLayer(this.getFirstLayer().getInputNeuronCount()
					+ getFirstLayer().getOutputNeuronCount() + (getFirstLayer().hasBiasUnit() ? 1 : 0), getFirstLayer().getInputNeuronCount()
					+ getFirstLayer().getOutputNeuronCount() + (getFirstLayer().hasBiasUnit ? 1 : 0), thetas1, new SigmoidActivationFunction(), false,
					true);
			layers.add(hiddenLayer);

		}

		DoubleMatrix thetas2 = new DoubleMatrix(outputCount, inputCountWithBias + hiddenCount + 1);
		DoubleMatrix t2 = initialRetrainableThetas.get(1);

		for (int r1 = 0; r1 < t2.getRows(); r1++) {
			for (int c1 = 0; c1 < t2.getColumns(); c1++) {
				if (c1 == 0) {
					thetas2.put(r1, 0, t2.get(r1, 0));
				} else {
					thetas2.put(r1, c1 + inputCountWithBias, t2.get(r1, c1));
				}
			}
		}

		FeedForwardLayer finalLayer = new FeedForwardLayer(this.getFirstLayer().getInputNeuronCount()
				+ this.getFirstLayer().getOutputNeuronCount() + 1, this.getOuterLayer().getOutputNeuronCount(),
				thetas2, new SigmoidActivationFunction(), true, true);

		layers.add(finalLayer);
		FeedForwardNeuralNetwork unrolled = new FeedForwardNeuralNetwork(layers);

		return unrolled;
	}

	private Vector<DoubleMatrix> getUpdatedThetasForSequence(int sequenceLength, Vector<DoubleMatrix> initialRetrainableThetas,
			DoubleMatrix inputSeq, DoubleMatrix outputSeq,double[] regularizationLambdas) {
		
		// Setup variables
		int inputCount = this.getLayers().get(0).getInputNeuronCount();
		int inputCountWithBias = inputCount + (this.getFirstLayer().hasBiasUnit() ? 1 : 0);
		int hiddenCount = this.getLayers().get(0).getOutputNeuronCount();
		int outputCount = this.getLayers().get(1).getOutputNeuronCount();
		DoubleMatrix initialHidden = new DoubleMatrix(1, this.getFirstLayer().getOutputNeuronCount());


		// Unfold Neural Network in time
		FeedForwardNeuralNetwork unrolled = createUnrolledNeuralNetwork(sequenceLength, inputCountWithBias,
				hiddenCount, outputCount, initialRetrainableThetas);

		// Forward Propagate the inputSequence 
		ForwardPropagation fp = createForwardPropagation(unrolled, inputCountWithBias,sequenceLength, inputSeq,initialHidden);
		
		DoubleMatrix outputs = outputSeq.getColumn(outputSeq.getColumns() - 1);
		double[] lambdas = new double[unrolled.getNumberOfLayers()];
		for (int l = 0; l < lambdas.length -1; l++)
		{
			lambdas[l] = regularizationLambdas[0];
		}
		lambdas[lambdas.length - 1] = regularizationLambdas[1];
		
		// Back Propagate the errors for the output to be the target sequence value at this time
		BackPropagation backPropagation = unrolled.backPropagate(fp, outputs, lambdas);

		// Get the gradients from back prop for all time-steps and final unfolded layer
		List<NeuralNetworkLayerErrorGradient> layerGradients = backPropagation.getGradientsForRetrainableLayers();

		
		// Convert to average gradients for each layer of our original recurrent Neural Network
		List<DoubleMatrix> gradMatrices = createRecurrentGradientMatricesFromUnfoldedGradientMatrices(layerGradients,inputCount,inputCountWithBias,hiddenCount,outputCount);

		// Perform gradient update of thetas
		Vector<DoubleMatrix> updatedThetas = new Vector<DoubleMatrix>();
		
		for (int in = 0; in < initialRetrainableThetas.size(); in++) {
			DoubleMatrix g = gradMatrices.get(in);
			DoubleMatrix thetas = initialRetrainableThetas.get(in).dup();
			thetas.subi(g.mul(trainingRate));
			updatedThetas.add(thetas);
		}

		return updatedThetas;
	}

	private List<DoubleMatrix> createRecurrentGradientMatricesFromUnfoldedGradientMatrices(List<NeuralNetworkLayerErrorGradient> layerGradients,int inputCount,int inputCountWithBias,int hiddenCount,int outputCount) {
		
		List<DoubleMatrix> recurrentGradientMatrices = new ArrayList<DoubleMatrix>();

		
		// Average the inputHidden gradients over time
		DoubleMatrix averageInputHiddenGradient = null;
		int cnt = 0;
		for (int in = 0; in < layerGradients.size() - 1; in++) {
			NeuralNetworkLayerErrorGradient g = layerGradients.get(in);

			if (averageInputHiddenGradient == null) {
				averageInputHiddenGradient = g.getErrorGradient();
				;
			} else {
				averageInputHiddenGradient = averageInputHiddenGradient.add(g.getErrorGradient());
			}
			cnt++;
		}
		averageInputHiddenGradient.divi(cnt++);
		
		// Get the outer layer gradient
		DoubleMatrix outerGradient = layerGradients.get(layerGradients.size() - 1).getErrorGradient();



		int[] inputHiddenGradientRowsForRecurrentHiddenUnits = new int[hiddenCount];
		for (int it = 0; it < hiddenCount; it++) {
			inputHiddenGradientRowsForRecurrentHiddenUnits[it] = it + inputCountWithBias;
		}
			
		int[] hiddenOutputGradientColumnsForRecurrentOutputUnits = new int[hiddenCount + 1];
		hiddenOutputGradientColumnsForRecurrentOutputUnits[0] = 0;
		for (int y = 1; y < hiddenCount + 1; y++) {
			hiddenOutputGradientColumnsForRecurrentOutputUnits[y] = y + inputCountWithBias;
		}


		DoubleMatrix recurrentInputHiddenGrad = averageInputHiddenGradient.getRows(inputHiddenGradientRowsForRecurrentHiddenUnits);

		

		DoubleMatrix recurrentOuterGrad = outerGradient.getColumns(hiddenOutputGradientColumnsForRecurrentOutputUnits);

		recurrentGradientMatrices.add(recurrentInputHiddenGrad);
		recurrentGradientMatrices.add(recurrentOuterGrad);
		
		return recurrentGradientMatrices;
	}

	protected DoubleMatrix getMinimisingThetasForRetrainableLayers(DoubleMatrix[] allInputs,
			DoubleMatrix[] allDesiredOutputs, Vector<DoubleMatrix> initialRetrainableThetas,
			double[] retrainableLambdas, CostFunction costFunction, int max_iter) {
		
		RecurrentNeuralNetwork duplicateNeuralNetwork = this.dup(false);

		DoubleMatrix newThetas = null;
		for (int iteration = 0; iteration < max_iter; iteration++) {
			
			
			for (int i = 1; i <= maxSequenceLength; i++) {
				DoubleMatrix inputsForSequenceLength = allInputs[i - 1];

				DoubleMatrix outputsForSequenceLength = allDesiredOutputs[i - 1];

				for (int r = 0; r < inputsForSequenceLength.getRows(); r++) {
					
					DoubleMatrix inputSeq = inputsForSequenceLength.getRow(r);
					DoubleMatrix outputSeq = outputsForSequenceLength.getRow(r);

					newThetas = NeuralNetworkUtils.reshapeToVector(duplicateNeuralNetwork.getUpdatedThetasForSequence(i, duplicateNeuralNetwork.getClonedRetrainableThetas(), inputSeq,
							outputSeq,retrainableLambdas));
					duplicateNeuralNetwork.updateThetasForRetrainableLayers(newThetas, true);


				}
			}
			double cost = duplicateNeuralNetwork.getCost(maxSequenceLength, allInputs, allDesiredOutputs,retrainableLambdas,costFunction);
			
			System.out.print("Iteration " + iteration + " | Cost: " + cost + "\r");

			

		}

		return newThetas;

	}

	private double getCost(int maxSequenceLength, DoubleMatrix[] allInputs, DoubleMatrix[] allOutputs,double[] regularizationLambdas,CostFunction costFunction) {

		RecurrentNeuralNetwork testNetwork = (RecurrentNeuralNetwork) this.dup(false);

		double J = 0;
		int count = 0;

		for (int it = 1; it <= maxSequenceLength; it++) {
			DoubleMatrix inputsForSequenceLength1 = allInputs[it - 1];

			DoubleMatrix outputsForSequenceLength1 = allOutputs[it - 1];

			for (int r1 = 0; r1 < inputsForSequenceLength1.getRows(); r1++) {

				for (int c = 0; c < inputsForSequenceLength1.getRow(r1).getColumns(); c++) {

					ForwardPropagation forwardPropagation1 = testNetwork.forwardPropagate(inputsForSequenceLength1
							.getRow(r1).getColumn(c));
					J = J
							+ forwardPropagation1.getCostWithRetrainableLayerRegularisation(outputsForSequenceLength1
									.getRow(r1).getColumn(c), regularizationLambdas, costFunction);
					count++;

				}
			}
		}
		double cost = J / count;
		return cost;
	}

	@Override
	public RecurrentNeuralNetwork dup(boolean allLayersRetrainable) {
		return new RecurrentNeuralNetwork((RecurrentLayer) getFirstLayer().dup(allLayersRetrainable || getFirstLayer().isRetrainable()), (FeedForwardLayer) 
				getOuterLayer().dup(allLayersRetrainable || getOuterLayer().isRetrainable()),maxSequenceLength, trainingRate);
	}

}