using System;
using System.Collections.Generic;
using pack.packable;

namespace test.example
{
    public class PackVo
    {
        public enum Result
        {
            SUCCESS,
            FAILED_1,
            FAILED_2,
            FAILED_3
        }

        public class Category : Packable
        {
            string name;
            int level;
            long i_column;
            double d_column;
            string des;
            Category[] sub_category;

            public void Encode(PackEncoder encoder)
            {
                encoder.PutString(0, name)
                        .PutInt(1, level)
                        .PutLong(2, i_column)
                        .PutCDouble(3, d_column)
                        .PutString(4, des)
                        .PutPackableArray(5, sub_category);
            }

            public override bool Equals(object obj)
            {
                var category = obj as Category;
                bool flag = true;
                flag &= category != null;
                flag &= name == category.name;
                flag &= level == category.level;
                flag &= i_column == category.i_column;
                flag &= d_column == category.d_column;
                flag &= des == category.des;
                if (flag && sub_category == null && category.sub_category != null)
                {
                    flag = false;
                }
                if (flag && sub_category != null && category.sub_category == null)
                {
                    flag = false;
                }
                if (flag && sub_category != null && category.sub_category != null)
                {
                    if (sub_category.Length == category.sub_category.Length)
                    {
                        for (int i = 0; i < sub_category.Length; i++)
                        {
                            flag &= sub_category[i].Equals(sub_category[i]);
                            if (!flag)
                            {
                                Console.WriteLine("category at:" + i);
                                break;
                            }
                        }
                    }
                    else
                    {
                        flag = false;
                    }
                }
                if (!flag)
                {
                    Console.WriteLine("category:" + name);
                }

                return flag;
            }

            public static PackCreator<Category> CREATOR = delegate (PackDecoder decoder)
            {
                Category c = new Category();
                c.name = decoder.GetString(0);
                c.level = decoder.GetInt(1);
                c.i_column = decoder.GetLong(2);
                c.d_column = decoder.GetCDouble(3);
                c.des = decoder.GetString(4);
                c.sub_category = decoder.GetPackableArray(5, CREATOR);
                return c;
            };
        }

        public class Data : Packable
        {
            bool d_bool;
            float d_float;
            double d_double;

            string string_1;

            int int_1;
            int int_2;
            int int_3;
            int int_4;
            int int_5;

            long long_1;
            long long_2;
            long long_3;
            long long_4;
            long long_5;

            Category d_categroy;

            bool[] bool_array;
            int[] int_array;
            long[] long_array;
            float[] float_array;
            double[] double_array;
            string[] string_array;

            public void Encode(PackEncoder encoder)
            {
                encoder.PutBoolean(0, d_bool);
                encoder.PutFloat(1, d_float);
                encoder.PutDouble(2, d_double);
                encoder.PutString(3, string_1);
                encoder.PutInt(4, int_1);
                encoder.PutInt(5, int_2);
                encoder.PutInt(6, int_3);
                encoder.PutSInt(7, int_4);
                encoder.PutInt(8, int_5);
                encoder.PutLong(9, long_1);
                encoder.PutLong(10, long_2);
                encoder.PutLong(11, long_3);
                encoder.PutSLong(12, long_4);
                encoder.PutLong(13, long_5);
                encoder.PutPackable(14, d_categroy);
                encoder.PutBooleanArray(15, bool_array);
                encoder.PutIntArray(16, int_array);
                encoder.PutLongArray(17, long_array);
                encoder.PutFloatArray(18, float_array);
                encoder.PutDoubleArray(19, double_array);
                encoder.PutStringArray(20, string_array);
            }

            public override bool Equals(object obj)
            {
                var data = obj as Data;
                bool flag = true;
                flag &= data != null;
                flag &= d_bool == data.d_bool;
                flag &= d_float == data.d_float;
                flag &= d_double == data.d_double;
                flag &= string_1 == data.string_1;
                flag &= int_1 == data.int_1;
                flag &= int_2 == data.int_2;
                flag &= int_3 == data.int_3;
                flag &= int_4 == data.int_4;
                flag &= int_5 == data.int_5;
                flag &= long_1 == data.long_1;
                flag &= long_2 == data.long_2;
                flag &= long_3 == data.long_3;
                flag &= long_4 == data.long_4;
                flag &= long_5 == data.long_5;
                flag &= d_categroy.Equals(data.d_categroy);
                if (!flag)
                {
                    Console.WriteLine("data not equal, long_5: " + long_5);
                    return false;
                }

                if (bool_array.Length != data.bool_array.Length)
                {
                    Console.WriteLine("bool_array len:" + bool_array.Length);
                    return false;
                }

                for (int i = 0; i < bool_array.Length; i++)
                {
                    if (bool_array[i] != data.bool_array[i])
                    {
                        Console.WriteLine("bool_array:" + i);
                        return false;
                    }
                }
                if (int_array.Length != data.int_array.Length)
                {
                    Console.WriteLine("int_array len:" + int_array.Length);
                    return false;
                }
                for (int i = 0; i < int_array.Length; i++)
                {
                    if (int_array[i] != data.int_array[i])
                    {
                        Console.WriteLine("int_array:" + i);
                        return false;
                    }
                }
                if (long_array.Length != data.long_array.Length)
                {
                    Console.WriteLine("long_array len:" + long_array.Length);
                    return false;
                }
                for (int i = 0; i < long_array.Length; i++)
                {
                    if (long_array[i] != data.long_array[i])
                    {
                        Console.WriteLine("long_array:" + i);
                        return false;
                    }
                }
                if (float_array.Length != data.float_array.Length)
                {
                    Console.WriteLine("float_array len:" + float_array.Length);
                    return false;
                }
                for (int i = 0; i < float_array.Length; i++)
                {
                    if (float_array[i] != data.float_array[i])
                    {
                        Console.WriteLine("float_array:" + i);
                        return false;
                    }
                }
                if (double_array.Length != data.double_array.Length)
                {
                    Console.WriteLine("double_array len:" + double_array.Length);
                    return false;
                }
                for (int i = 0; i < double_array.Length; i++)
                {
                    if (double_array[i] != data.double_array[i])
                    {
                        Console.WriteLine("double_array:" + i);
                        return false;
                    }
                }
                if (string_array.Length != data.string_array.Length)
                {
                    Console.WriteLine("string_array len:" + string_array.Length);
                    return false;
                }
                for (int i = 0; i < string_array.Length; i++)
                {
                    if (string_array[i] != data.string_array[i])
                    {
                        Console.WriteLine("double_array:" + i);
                        return false;
                    }
                }
                return flag;
            }

            public static PackCreator<Data> CREATOR = delegate (PackDecoder decoder)
            {
                Data d = new Data
                {
                    d_bool = decoder.GetBoolean(0),
                    d_float = decoder.GetFloat(1),
                    d_double = decoder.GetDouble(2),
                    string_1 = decoder.GetString(3),
                    int_1 = decoder.GetInt(4),
                    int_2 = decoder.GetInt(5),
                    int_3 = decoder.GetInt(6),
                    int_4 = decoder.GetSInt(7),
                    int_5 = decoder.GetInt(8),
                    long_1 = decoder.GetLong(9),
                    long_2 = decoder.GetLong(10),
                    long_3 = decoder.GetLong(11),
                    long_4 = decoder.GetSLong(12),
                    long_5 = decoder.GetLong(13),
                    d_categroy = decoder.GetPackable(14, Category.CREATOR),
                    bool_array = decoder.GetBooleanArray(15),
                    int_array = decoder.GetIntArray(16),
                    long_array = decoder.GetLongArray(17),
                    float_array = decoder.GetFloatArray(18),
                    double_array = decoder.GetDoubleArray(19),
                    string_array = decoder.GetStringArray(20)
                };
                return d;
            };
        }

        public class Response : Packable
        {
            Result code;
            string detail;
            Data[]
            data;

            public void Encode(PackEncoder encoder)
            {
                encoder.PutInt(0, (int)code);
                encoder.PutString(1, detail);
                encoder.PutPackableArray(2, data);
            }

            public override bool Equals(object obj)
            {
                var response = obj as Response;
                bool flag = true;
                flag &= response != null;
                flag &= code == response.code;
                flag &= detail == response.detail;
                flag &= data.Length == response.data.Length;
                if (flag)
                {
                    for (int i = 0; i < data.Length; i++)
                    {
                        flag &= data[i].Equals(response.data[i]);
                        if (!flag)
                        {
                            Console.WriteLine(i);
                            break;
                        }
                    }
                }
                return flag;
            }

            public static PackCreator<Response> CREATOR = delegate (PackDecoder decoder)
            {
                Response r = new Response
                {
                    code = (Result)decoder.GetInt(0),
                    detail = decoder.GetString(1),
                    data = decoder.GetPackableArray(2, Data.CREATOR)
                };
                return r;
            };
        }

    }// end of PackVo

    public class Info : Packable
    {
        public long id;
        public String name;
        public Rectangle rect;



        public void Encode(PackEncoder encoder)
        {
            encoder.PutLong(0, id)
                .PutString(1, name);
            PackBuffer buf = encoder.PutCustom(2, 16);
            buf.WriteInt(rect.x);
            buf.WriteInt(rect.y);
            buf.WriteInt(rect.width);
            buf.WriteInt(rect.height);
        }

        public override bool Equals(object obj)
        {
            return obj is Info info &&
                   id == info.id &&
                   name == info.name &&
                   EqualityComparer<Rectangle>.Default.Equals(rect, info.rect);
        }

        private static PackCreator<Info> cREATOR = delegate (PackDecoder decoder)
         {
             Info info = new Info();
             info.id = decoder.GetLong(0);
             info.name = decoder.GetString(1);
             PackBuffer buf = decoder.GetCustom(2);
             if (buf != null)
             {
                 Rectangle r = new Rectangle();
                 r.x = buf.ReadInt();
                 r.y = buf.ReadInt();
                 r.width = buf.ReadInt();
                 r.height = buf.ReadInt();
                 info.rect = r;
             }
             return info;
         };

        public static PackCreator<Info> CREATOR { get => cREATOR; set => cREATOR = value; }
    }

    public struct Rectangle
    {
        public int x;
        public int y;
        public int width;
        public int height;

        public Rectangle(int x, int y, int w, int h)
        {
            this.x = x;
            this.y = y;
            width = w;
            height = h;
        }
    }
}