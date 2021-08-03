using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using pack.packable;
using test.example;


using System.Diagnostics;

namespace pack
{
    class Program
    {
        public static bool ByteArraysEqual(byte[] b1, byte[] b2)
        {
            if (b1 == b2) return true;
            if (b1 == null || b2 == null) return false;
            if (b1.Length != b2.Length) return false;
            for (int i = 0; i < b1.Length; i++)
            {
                if (b1[i] != b2[i])
                {
                    Console.WriteLine("ByteArraysEqual: {0},{1},{2}", i, b1[i], b2[i]);
                    return false;
                }
            }
            return true;
        }

        static void Main(string[] args)
        {
            string path = "./../../../../../test_data/packable_2000.data";
            byte[] bytes = File.ReadAllBytes(path);

            bool equal = true;
            try
            {
                int r = 5;
                for (int i = 0; i < r; i++)
                {
                    PackVo.Response response = PackDecoder.Unmarshal(bytes, PackVo.Response.CREATOR);
                    byte[] packData = PackEncoder.Marshal(response);
                    if ((bytes.Length != packData.Length) || !ByteArraysEqual(bytes, packData))
                    {
                        Console.WriteLine("Length: {0}, {1}", bytes.Length, packData.Length);
                        equal = false;
                        break;
                    }

                    PackVo.Response response2 = PackDecoder.Unmarshal(packData, PackVo.Response.CREATOR);
                    if (!response.Equals(response2))
                    {
                        Console.WriteLine("object not equal:");
                        equal = false;
                        break;
                    }
                }

            }
            catch (Exception e)
            {
                equal = false;
                Console.WriteLine("error:" + e.StackTrace);
            }

            if (equal)
            {
                long encodeTime = 0;
                long decodeTime = 0;
                Stopwatch watch = new Stopwatch();
                int r = 5;
                for (int i = 0; i < r; i++)
                {
                    watch.Start();
                    PackVo.Response response = PackDecoder.Unmarshal(bytes, PackVo.Response.CREATOR);
                    watch.Stop();
                    decodeTime += watch.ElapsedMilliseconds;
                    watch.Restart();
                    byte[] packData = PackEncoder.Marshal(response);
                    watch.Stop();
                    encodeTime += watch.ElapsedMilliseconds;
                    watch.Reset();
                }

                encodeTime /= r;
                decodeTime /= r;

                Console.WriteLine("encode:{0} decode:{1}", encodeTime, decodeTime);
            }

            // TestCustomEncode();
        }

        static void TestCustomEncode()
        {
            Info info = new Info();
            info.id = 1234;
            info.name = "rect_1";
            info.rect = new Rectangle(100, 200, 300, 400);
            byte[] bytes = PackEncoder.Marshal(info);
            Info dInfo = PackDecoder.Unmarshal(bytes, Info.CREATOR);
            Console.WriteLine("TestCustomEncode:{0}", info.Equals(dInfo));
        }

    }
}
