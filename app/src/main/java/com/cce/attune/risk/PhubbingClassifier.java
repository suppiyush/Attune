package com.cce.attune.risk;

import android.content.Context;
import android.util.Log;

import com.cce.attune.features.PhubbingFeatures;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxMap;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public class PhubbingClassifier {

    private static final String TAG = "PhubbingClassifier";

    private static final String MODEL_FILE = "phubbing_model.onnx";
    private static final String SCALER_FILE = "scaler_params.json";

    private static final float STUB_SCORE = 0.5f;

    private static final int NUM_FEATURES = 5;

    private OrtEnvironment env;
    private OrtSession session;

    private double[] featureMeans;
    private double[] featureScales;

    // -----------------------------------------------------
    // Constructor
    // -----------------------------------------------------

    public PhubbingClassifier(Context context) {

        try {

            featureMeans = new double[NUM_FEATURES];
            featureScales = new double[NUM_FEATURES];

            loadScalerParams(context, featureMeans, featureScales);

            Log.d(TAG,"Scaler loaded");

        } catch (Exception e) {

            Log.w(TAG,"Scaler load failed: "+e.getMessage());

            featureMeans = null;
            featureScales = null;
        }

        try {

            env = OrtEnvironment.getEnvironment();

            byte[] modelBytes = loadModelFile(context);

            session = env.createSession(modelBytes, new OrtSession.SessionOptions());

            Log.d(TAG,"ONNX model loaded");

            Log.d(TAG,"Model Inputs: "+session.getInputNames());
            Log.d(TAG,"Model Outputs: "+session.getOutputNames());

            session.getInputInfo().forEach((name,info)->{
                Log.d(TAG,"InputInfo "+name+" : "+info.getInfo());
            });

            session.getOutputInfo().forEach((name,info)->{
                Log.d(TAG,"OutputInfo "+name+" : "+info.getInfo());
            });

        }
        catch (Exception e){

            Log.e(TAG,"Model load failed "+e.getMessage());

            session = null;
        }
    }

    // -----------------------------------------------------
    // Predict
    // -----------------------------------------------------

    public float predict(PhubbingFeatures f){

        if(session == null || env == null){

            Log.w(TAG,"Model unavailable returning stub");

            return STUB_SCORE;
        }

        try{

            // -----------------------------------
            // Raw feature vector
            // -----------------------------------

            float[] raw = new float[NUM_FEATURES];

            raw[0] = f.unlockRate;
            raw[1] = f.switchRate;
            raw[2] = f.avgSessionDurationSeconds;
            raw[3] = f.socialAppLaunches;
            raw[4] = f.notificationReactionSeconds;

            Log.d(TAG,"Raw features "+
                    raw[0]+","+raw[1]+","+raw[2]+","+raw[3]+","+raw[4]);

            // -----------------------------------
            // Scaling
            // -----------------------------------

            float[] scaled = applyScaling(raw);

            Log.d(TAG,"Scaled features "+
                    scaled[0]+","+scaled[1]+","+scaled[2]+","+scaled[3]+","+scaled[4]);

            // -----------------------------------
            // Tensor
            // -----------------------------------

            float[][] inputData = new float[][]{scaled};

            String inputName = session.getInputNames().iterator().next();

            Log.d(TAG,"Input tensor name "+inputName);

            try(OnnxTensor tensor = OnnxTensor.createTensor(env,inputData)){

                Map<String,OnnxTensor> inputs =
                        Collections.singletonMap(inputName,tensor);

                try(OrtSession.Result output = session.run(inputs)){

                    Log.d(TAG,"Outputs count "+output.size());

                    for(int i=0;i<output.size();i++){

                        Object obj = output.get(i).getValue();

                        Log.d(TAG,"Output["+i+"] type "+obj.getClass());
                    }

                    // -----------------------------------
                    // Label output
                    // -----------------------------------

                    Object labelObj = output.get(0).getValue();

                    if(labelObj instanceof long[]){

                        long[] labels = (long[]) labelObj;

                        Log.d(TAG,"Predicted label "+labels[0]);
                    }

                    // -----------------------------------
                    // Probability output
                    // -----------------------------------

                    Object probObj = output.get(1).getValue();

                    Log.d(TAG,"Probability object "+probObj.getClass());

                    if(probObj instanceof List){

                        List<?> list = (List<?>) probObj;

                        if(list.size()==0){

                            Log.e(TAG,"Probability list empty");

                            return STUB_SCORE;
                        }

                        Object first = list.get(0);

                        Log.d(TAG,"First element type "+first.getClass());

                        if(first instanceof OnnxMap){

                            OnnxMap onnxMap = (OnnxMap) first;

                            Map<?,?> map = onnxMap.getValue();

                            Log.d(TAG,"Probability map "+map);

                            float score = 0f;

                            if(map.containsKey(1L)){

                                score = ((Number)map.get(1L)).floatValue();
                            }

                            Log.d(TAG,"Phubbing probability "+score);

                            return clamp(score);
                        }
                    }

                    Log.w(TAG,"Probability output parse failed");

                    return STUB_SCORE;
                }
            }

        }
        catch(Exception e){

            Log.e(TAG,"Inference error "+e.getMessage(),e);

            return STUB_SCORE;
        }
    }

    // -----------------------------------------------------
    // Scaling
    // -----------------------------------------------------

    private float[] applyScaling(float[] raw){

        float[] scaled = new float[NUM_FEATURES];

        if(featureMeans==null || featureScales==null){

            Log.w(TAG,"Scaler unavailable using raw");

            System.arraycopy(raw,0,scaled,0,NUM_FEATURES);

            return scaled;
        }

        for(int i=0;i<NUM_FEATURES;i++){

            double scale = featureScales[i];

            if(scale==0){

                scaled[i] = 0f;
            }
            else{

                scaled[i] =
                        (float)((raw[i]-featureMeans[i]) / scale);
            }

            Log.d(TAG,"Scale feature["+i+"] raw="+raw[i]+
                    " mean="+featureMeans[i]+
                    " scale="+scale+
                    " scaled="+scaled[i]);
        }

        return scaled;
    }

    // -----------------------------------------------------
    // Clamp
    // -----------------------------------------------------

    private float clamp(float s){

        if(s<0f) return 0f;

        if(s>1f) return 1f;

        return s;
    }

    // -----------------------------------------------------
    // Load scaler
    // -----------------------------------------------------

    private void loadScalerParams(Context context,
                                  double[] means,
                                  double[] scales) throws Exception{

        InputStream is =
                context.getAssets().open(SCALER_FILE);

        byte[] buffer = new byte[is.available()];

        is.read(buffer);

        is.close();

        String json =
                new String(buffer, StandardCharsets.UTF_8);

        JSONObject root = new JSONObject(json);

        var meanArray = root.getJSONArray("mean");
        var scaleArray = root.getJSONArray("scale");

        for(int i=0;i<NUM_FEATURES;i++){

            means[i] = meanArray.getDouble(i);

            scales[i] = scaleArray.getDouble(i);
        }

        Log.d(TAG,"Scaler JSON parsed");
    }

    // -----------------------------------------------------
    // Load model
    // -----------------------------------------------------

    private byte[] loadModelFile(Context context) throws java.io.IOException{

        InputStream is =
                context.getAssets().open(MODEL_FILE);

        byte[] buffer = new byte[is.available()];

        is.read(buffer);

        is.close();

        Log.d(TAG,"Model loaded from assets");

        return buffer;
    }

    // -----------------------------------------------------
    // Close
    // -----------------------------------------------------

    public void close(){

        try{

            if(session!=null) session.close();

            if(env!=null) env.close();
        }
        catch(OrtException e){

            Log.e(TAG,"ONNX close error",e);
        }
    }
}