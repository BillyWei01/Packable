import com.example.DataGenerator;
import com.example.IOUtil;
import com.example.PackVo;
import com.example.ProtoVo;
import com.google.gson.Gson;
import io.packable.PackDecoder;
import io.packable.PackEncoder;

import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        //outputData(2000);

        // warming up
        testPerformance(500, false);

        testPerformance(500, true);
        testPerformance(1000, true);
        testPerformance(2000, true);


    }

    private static void testPerformance(int n, boolean printResult) {
        try {
            long a1 = 0, a2 = 0, a3 = 0, a4 = 0, a5 = 0, a6 = 0;
            long l1 = 0, l2 = 0, l3 = 0;
            int r = 5;
            for (int i = 0; i < r; i++) {
                ProtoVo.Response protoResponse = DataGenerator.generateProtoData(n);
                PackVo.Response packResponse = DataGenerator.convertProtoVoToPackVo(protoResponse);
                Gson gson = new Gson();
                long t1 = System.nanoTime();

                byte[] packData = PackEncoder.marshal(packResponse);

                long t2 = System.nanoTime();

                PackVo.Response packR = PackDecoder.unmarshal(packData, PackVo.Response.CREATOR);

                long t3 = System.nanoTime();

                byte[] protoData = protoResponse.toByteArray();

                long t4 = System.nanoTime();

                ProtoVo.Response protoR = ProtoVo.Response.parseFrom(protoData);

                long t5 = System.nanoTime();

                String json = gson.toJson(packResponse);

                long t6 = System.nanoTime();

                PackVo.Response gsonR = gson.fromJson(json, PackVo.Response.class);

                long t7 = System.nanoTime();

                a1 += t2 - t1;
                a2 += t3 - t2;
                a3 += t4 - t3;
                a4 += t5 - t4;
                a5 += t6 - t5;
                a6 += t7 - t6;

                if (l1 != 0L && l1 != packData.length) {
                    throw new Exception("l1 != packData.length");
                }
                l1 = packData.length;
                l2 = protoData.length;
                l3 = json.getBytes().length;

                if (!packResponse.equals(packR)) {
                    throw new Exception("packResponse != packR");
                }
                if (!protoResponse.equals(protoR)) {
                    throw new Exception("protoResponse != packR");
                }
                if (!packResponse.equals(gsonR)) {
                    throw new Exception("packResponse != gsonR");
                }
            }

            if (printResult) {
                a1 = a1 / r / 1000000;
                a2 = a2 / r / 1000000;
                a3 = a3 / r / 1000000;
                a4 = a4 / r / 1000000;
                a5 = a5 / r / 1000000;
                a6 = a6 / r / 1000000;

                System.out.println("data count:" + n);
                System.out.println("packable bytes:" + l1 + " encode:" + a1 + " decode:" + a2);
                System.out.println("protobuf bytes:" + l2 + " encode:" + a3 + " decode:" + a4);
                System.out.println("gson     bytes:" + l3 + " encode:" + a5 + " decode:" + a6);
                System.out.println(" ");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    static void outputData(int n) throws Exception {
        ProtoVo.Response protoResponse = DataGenerator.generateProtoData(n);
        PackVo.Response packResponse = DataGenerator.convertProtoVoToPackVo(protoResponse);
        byte[] packData = PackEncoder.marshal(packResponse);
        PackVo.Response packR = PackDecoder.unmarshal(packData, PackVo.Response.CREATOR);
        if (!packResponse.equals(packR)) {
            throw new Exception("packResponse != packR");
        }
        File file = new File("../test_data/packable_" + n + ".data");
        IOUtil.bytesToFile(packData, file);
        System.out.println(file.getAbsolutePath());
        System.out.println("output data len: " + packData.length);
    }
}
