package edu.ml.tensorflow;

import edu.ml.tensorflow.classifier.YOLOClassifier;
import edu.ml.tensorflow.model.Recognition;
import edu.ml.tensorflow.util.GraphBuilder;
import edu.ml.tensorflow.util.IOUtil;
import edu.ml.tensorflow.util.ImageUtil;
import edu.ml.tensorflow.util.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.Graph;
import org.tensorflow.Output;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.ml.tensorflow.Config.GRAPH_FILE;
import static edu.ml.tensorflow.Config.LABEL_FILE;
import static edu.ml.tensorflow.Config.MEAN;
import static edu.ml.tensorflow.Config.SIZE;

/**
 * ObjectDetector class to detect objects using pre-trained models with TensorFlow Java API.
 */
public class ObjectDetector {
    private final static Logger LOGGER = LoggerFactory.getLogger(ObjectDetector.class);
    private byte[] GRAPH_DEF;
    private List<String> LABELS;
    
    private Graph yoloGraph;
    private Session yoloSession;
    private Session.Runner yoloRunner;

    public ObjectDetector(String graphFile, String labelFile) {
        try {
            GRAPH_DEF = IOUtil.readAllBytesOrExit(graphFile);
            LABELS = IOUtil.readAllLinesOrExit(labelFile);
        } catch (ServiceException ex) {
            LOGGER.error("Download one of my graph file to run the program! \n" +
                    "You can find my graphs here: https://drive.google.com/open?id=1GfS1Yle7Xari1tRUEi2EDYedFteAOaoN");
        }
        
        yoloGraph = createYoloGraph();
        yoloSession = new Session(yoloGraph);
        yoloRunner = yoloSession.runner();
    }

    /**
     * Detect objects on the given image
     * @param imageLocation the location of the image
     */
//    public void detect(byte[] namirTest, final String imageLocation) {
    public List<Map> detect(final byte[] image) {
        try (Tensor<Float> normalizedImage = normalizeImage(image)) {
            List<Recognition> recognitions = YOLOClassifier.getInstance().classifyImage(executeYOLOGraph(normalizedImage), LABELS);
            
            //Namir's Method
            return returnJSON(recognitions);
        }
    }

    /**
     * Pre-process input. It resize the image and normalize its pixels
     * @param imageBytes Input image
     * @return Tensor<Float> with shape [1][416][416][3]
     */
    private Tensor<Float> normalizeImage(final byte[] imageBytes) {
        try (Graph graph = new Graph()) {
            GraphBuilder graphBuilder = new GraphBuilder(graph);

            final Output<Float> output =
                graphBuilder.div( // Divide each pixels with the MEAN
                    graphBuilder.resizeBilinear( // Resize using bilinear interpolation
                            graphBuilder.expandDims( // Increase the output tensors dimension
                                    graphBuilder.cast( // Cast the output to Float
                                            graphBuilder.decodeJpeg(
                                                    graphBuilder.constant("input", imageBytes), 3),
                                            Float.class),
                                    graphBuilder.constant("make_batch", 0)),
                            graphBuilder.constant("size", new int[]{SIZE, SIZE})),
                    graphBuilder.constant("scale", MEAN));

            try (Session session = new Session(graph)) {
                return session.runner().fetch(output.op().name()).run().get(0).expect(Float.class);
            }
        }
    }

    private Graph createYoloGraph() {
        Graph g = new Graph();
        g.importGraphDef(GRAPH_DEF);
        return g;
    }
    
    /**
     * Executes graph on the given preprocessed image
     * @param image preprocessed image
     * @return output tensor returned by tensorFlow
     */
    private float[] executeYOLOGraph(final Tensor<Float> image) {
//        long preSess = System.currentTimeMillis();
//        try (Session s = new Session(yoloGraph)){
//            Session.Runner r = s.runner().feed("input", image).fetch("output");
//            long postSess = System.currentTimeMillis();
//            LOGGER.debug("Session runner creation time: " + (postSess - preSess) / 1000 + "." + (postSess - preSess) % 1000 + " seconds");
            long preRun = System.currentTimeMillis();
            yoloRunner = yoloSession.runner().feed("input", 0, image).fetch("output");
            try(Tensor<Float> result = 
                    yoloRunner.run().get(0).expect(Float.class)) {
                long postRun = System.currentTimeMillis();
                LOGGER.debug("Session run time: " + (postRun - preRun) / 1000 + "." + (postRun - preRun) % 1000 + " seconds");
                float[] outputTensor = new float[YOLOClassifier.getInstance().getOutputSizeByShape(result)];
                FloatBuffer floatBuffer = FloatBuffer.wrap(outputTensor);
                result.writeTo(floatBuffer);
                return outputTensor;
            }
//        }
    }
    
    /**
     * ADDED BY NAMIR - Used to convert recognitions to JSON
     * @param recognitions
     */
    private List<Map> returnJSON(final List<Recognition> recognitions) {
    	List<Map> jsonRecognitions = new ArrayList<Map>();
        for (Recognition recognition : recognitions) {
            //recognition.getTitle(), recognition.getConfidence(), recognition.getLocation());
        	HashMap map = new HashMap();
        	map.put("label", recognition.getTitle());
        	map.put("confidence", recognition.getConfidence().toString());
        	
        	HashMap location = new HashMap();
        	location.put("left", recognition.getLocation().getLeft());
        	location.put("top", recognition.getLocation().getTop());
        	location.put("right", recognition.getLocation().getRight());
        	location.put("bottom", recognition.getLocation().getBottom());
        	map.put("location", location);
        	
        	jsonRecognitions.add(map);
        	
        	LOGGER.info(map.toString());
        }
        
        return jsonRecognitions;
    }
    
    public void close() {
        if (yoloGraph != null) {
            yoloGraph.close();
            yoloGraph = null;
        }
        if (yoloSession != null) {
            yoloSession.close();
            yoloSession = null;
        }
        yoloRunner = null;
    }
    
    @Override
    protected void finalize() {
        close();
    }
}
