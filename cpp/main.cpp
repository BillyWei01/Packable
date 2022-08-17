
#include <stdint.h>
#include <stdio.h>
#include <fstream> 
#include "packable/PackEncoder.h"
#include "packable/PackDecoder.h"
#include "example/PackVo.h"
#include "example/Test.h"

void testEncodeAndDecode() {
    std::ifstream is("../test_data/packable_2000.data", std::ifstream::binary);

    if (is) {
        is.seekg(0, is.end);
        int length = is.tellg();
        is.seekg(0, is.beg);

        char *buffer = new char[length];
        char *buffer2 = nullptr;

        int cnt = 0;
        while (!is.eof() && cnt < length) {
            is.read(buffer + cnt, length - cnt);
            cnt += is.gcount();
        }
        is.close();

        printf("file length:%d, read %d bytes\n", length, cnt);

        try {
            for (int i = 0; i < 3; i++) {
                clock_t t1 = clock();
                Response *response = PackDecoder::unmarshal(buffer, length, Response::decode);
                clock_t t2 = clock();
                int count = 0;
                buffer2 = PackEncoder::marshal(response, count);
                clock_t t3 = clock();
                int equal = length == count && memcmp(buffer, buffer2, length) == 0;
                delete response;
                if (buffer2 != nullptr) {
                    delete[] buffer2;
                }
    
                if (!equal) {
                    printf("count:%d  equal:%d\n", count, equal);
                }

                printf("decode:%ld,  encode:%ld\n", (t2 - t1) / 1000, (t3 - t2) / 1000);
            }
            delete[] buffer;
        } catch (const char *msg) {
            delete[] buffer;
            printf("exception: %s\n", msg);
        }
    } else {
        printf("file not exit\n");
    }
    
    testAll();
}
     
int main()
{
    testEncodeAndDecode();
    return 0;
}
