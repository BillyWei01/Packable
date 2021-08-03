package com.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DataGenerator {

    private static ProtoVo.Result randomResult() {
        switch (RandomUtil.randomCount(4)) {
            case 1:
                return ProtoVo.Result.FAILED_1;
            case 2:
                return ProtoVo.Result.FAILED_2;
            case 3:
                return ProtoVo.Result.FAILED_3;
            default:
                return ProtoVo.Result.SUCCESS;

        }
    }

    private static ProtoVo.Category.Builder randomCategory(int level) {
        ProtoVo.Category.Builder builder = ProtoVo.Category.newBuilder()
                .setName(RandomUtil.randomShortString())
                .setLevel(level)
                .setIColumn(RandomUtil.randomLong())
                .setDColumn(RandomUtil.randomSmallDouble());
        String des = RandomUtil.randomNullableString();
        if (des != null) {
            builder.setDes(des);
        }
        return builder;
    }

    private static ProtoVo.Category makeCategory() {
        int subCount = RandomUtil.randomCount(12);
        ProtoVo.Category[] subCategoryArray = new ProtoVo.Category[subCount];
        for (int i = 0; i < subCount; i++) {
            subCategoryArray[i] = randomCategory(2).build();
        }
        return randomCategory(1)
                .addAllSubCategory(Arrays.asList(subCategoryArray))
                .build();
    }

    private static List<ProtoVo.Data> makeDataList(int n) {
        List<ProtoVo.Data> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ProtoVo.Data data = ProtoVo.Data.newBuilder()
                    .setDBool(RandomUtil.randomBoolean())
                    .setDFloat(RandomUtil.randomFloat())
                    .setDDouble(RandomUtil.randomDouble())
                    .setString1(RandomUtil.randomString())
                    .setInt1(RandomUtil.randomInt())
                    .setInt2(RandomUtil.randomInt())
                    .setInt3(RandomUtil.randomInt())
                    .setInt4(RandomUtil.randomSignedInt())
                    .setInt5(RandomUtil.randomInt())
                    .setLong1(RandomUtil.randomLong())
                    .setLong2(RandomUtil.randomLong())
                    .setLong3(RandomUtil.randomLong())
                    .setLong4(RandomUtil.randomSignedLong())
                    .setLong5(RandomUtil.randomLong())
                    .setDCategroy(makeCategory())
                    .addAllBoolArray(RandomUtil.randomBoolList())
                    .addAllIntArray(RandomUtil.randomIntList())
                    .addAllLongArray(RandomUtil.randomLongList())
                    .addAllFloatArray(RandomUtil.randomFloatList())
                    .addAllDoubleArray(RandomUtil.randomDoubleList())
                    .addAllStringArray(RandomUtil.randomStringList())
                    .build();
            list.add(data);
        }
        return list;
    }

    private static PackVo.Result protoResultToPackResult(ProtoVo.Result r) {
        switch (r) {
            case FAILED_1:
                return PackVo.Result.FAILED_1;
            case FAILED_2:
                return PackVo.Result.FAILED_2;
            case FAILED_3:
                return PackVo.Result.FAILED_3;
            default:
                return PackVo.Result.SUCCESS;
        }
    }

    private static void wrapCategory(PackVo.Category y, ProtoVo.Category x) {
        y.name = x.getName();
        y.level = x.getLevel();
        y.i_column = x.getIColumn();
        y.d_column = x.getDColumn();
        y.des = x.getDes().isEmpty() ? null : x.getDes();
    }

    private static PackVo.Category convertProtoCategoryToPackCategory(ProtoVo.Category x) {
        PackVo.Category y = new PackVo.Category();
        wrapCategory(y, x);
        List<ProtoVo.Category> cList = x.getSubCategoryList();
        int n = cList.size();
        y.sub_category = new PackVo.Category[n];
        for (int i = 0; i < n; i++) {
            PackVo.Category sub = new PackVo.Category();
            wrapCategory(sub, cList.get(i));
            y.sub_category[i] = sub;
        }
        return y;
    }

    private static PackVo.Data convertProtoDataToPackData(ProtoVo.Data x) {
        PackVo.Data y = new PackVo.Data();
        y.d_bool = x.getDBool();
        y.d_float = x.getDFloat();
        y.d_double = x.getDDouble();
        y.string_1 = x.getString1();
        y.int_1 = x.getInt1();
        y.int_2 = x.getInt2();
        y.int_3 = x.getInt3();
        y.int_4 = x.getInt4();
        y.int_5 = x.getInt5();
        y.long_1 = x.getLong1();
        y.long_2 = x.getLong2();
        y.long_3 = x.getLong3();
        y.long_4 = x.getLong4();
        y.long_5 = x.getLong5();
        y.d_category = convertProtoCategoryToPackCategory(x.getDCategroy());
        y.bool_array = RandomUtil.toBooleanList(x.getBoolArrayList());
        y.int_array = RandomUtil.toIntList(x.getIntArrayList());
        y.long_array = RandomUtil.toLongList(x.getLongArrayList());
        y.float_array = RandomUtil.toFloatList(x.getFloatArrayList());
        y.double_array = RandomUtil.toDoubleList(x.getDoubleArrayList());
        y.string_array = RandomUtil.toStringList(x.getStringArrayList());
        return y;
    }

    public static PackVo.Response convertProtoVoToPackVo(ProtoVo.Response x) {
        PackVo.Response y = new PackVo.Response();
        y.code = protoResultToPackResult(x.getCode());
        y.detail = x.getDetail();
        List<ProtoVo.Data> testList = x.getDataList();
        List<PackVo.Data> packList = new ArrayList<>(testList.size());
        for (ProtoVo.Data d : testList) {
            packList.add(convertProtoDataToPackData(d));
        }
        y.data = packList.toArray(new PackVo.Data[packList.size()]);
        return y;
    }


    public static ProtoVo.Response generateProtoData(int n) {
        RandomUtil.random.setSeed(1);
        return ProtoVo.Response.newBuilder()
                .setCode(randomResult())
                .setDetail(RandomUtil.randomShortString())
                .addAllData(makeDataList(n))
                .build();
    }
}
