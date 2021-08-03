
#include "Test.h"
#include <stdio.h>

class Rectangle{
public:
    int x;
    int y;
    int width;
    int height;
    
    Rectangle(int x, int y, int w, int h)
    {
        this->x = x;
        this->y = y;
        width = w;
        height = h;
    }
    
    bool operator==(const Rectangle &rhs) const {
        return static_cast<const void *>(this) == static_cast<const void *>(&rhs) ||
        (x == rhs.x &&
         y == rhs.y &&
         width == rhs.width &&
         height == rhs.height);
    }
    
    bool operator!=(const Rectangle &rhs) const {
        return !(rhs == *this);
    }
};

class Info : public Packable {
public:
    int64_t id;
    string name;
    Rectangle* rect;
    
    ~Info() {
        if(rect != nullptr){
            delete rect;
        }
    }
    
    void encode(PackEncoder &encoder) override {
        encoder.putInt64(0, id)
                .putString(1, &name);
        PackBuffer* buf = encoder.putCustom(2, 16);
        buf->writeInt(rect->x);
        buf->writeInt(rect->y);
        buf->writeInt(rect->width);
        buf->writeInt(rect->height);
    }
    
    bool operator==(const Info &rhs) const {
        return static_cast<const void *>(this) == static_cast<const void *>(&rhs) ||
        (id == rhs.id &&
         name == rhs.name &&
         *rect == *rhs.rect);
    }
    
    bool operator!=(const Info &rhs) const {
        return !(rhs == *this);
    }
    
    static Info *decode(PackDecoder &decoder);
};

Info *Info::decode(PackDecoder &decoder) {
    Info* info = new Info();
    info->id = decoder.getInt64(0);
    info->name = decoder.getString(1);
    PackBuffer* buf = decoder.getCustom(2);
    if (buf != nullptr) {
        info->rect = new Rectangle(
                                   buf->readInt(),
                                   buf->readInt(),
                                   buf->readInt(),
                                   buf->readInt());
    }
    return info;
}

void testCustomEncode() {
    Info* info = new Info();
    info->id = 1234;
    info->name = "rect_1";
    info->rect = new Rectangle(100, 200, 300, 400);
    int bytesCount;
    char * bytes = PackEncoder::marshal(info, bytesCount);
    Info* dInfo = PackDecoder::unmarshal(bytes, bytesCount, Info::decode);
    printf("testCustomEncode %s\n", (*info == *dInfo) ?"ok":"no eqaul");
    delete[] bytes;
    delete info;
    delete dInfo;
}

class A : public Packable {
public:
    int x;
    
    ~A() {
    }
    
    A() {
        x = 100;
    }
    
    explicit A(int x) {
        this->x = x;
    }
    
    bool operator==(const A &rhs) const {
        return static_cast<const void *>(this) == static_cast<const void *>(&rhs) ||
        x == rhs.x;
    }
    
    bool operator!=(const A &rhs) const {
        return !(rhs == *this);
    }
    
    void encode(PackEncoder &encoder) override {
        encoder.putInt(0, x);
    }
    
    static A *decode(PackDecoder &decoder);
};

A *A::decode(PackDecoder &decoder) {
    return new A(decoder.getInt(0));
}


void testStrVector() {
    char *bytes = nullptr;
    try {
        vector<string> a;
        a.push_back("");
        a.push_back("test");
        a.push_back("test1");
        PackEncoder encoder = PackEncoder();
        encoder.putStrVector(0, a);
        int count;
        bytes = encoder.getBytes(count);
        
        PackDecoder decoder = PackDecoder(bytes, 0, count);
        vector<string> b;
        decoder.getStrVector(0, b);
        
        printf("string vector %s\n", (a == b) ? "ok" : "not equal!");
        
        delete[] bytes;
    } catch (const char *msg) {
        delete[] bytes;
        printf("exception: %s\n", msg);
    }
    
    try {
        list<string> a;
        a.push_back("list");
        a.push_back("test");
        a.push_back("test1");
        PackEncoder encoder = PackEncoder();
        encoder.putStrList(0, a);
        int count;
        bytes = encoder.getBytes(count);
        
        PackDecoder decoder = PackDecoder(bytes, 0, count);
        vector<string> t;
        decoder.getStrVector(0, t);
        std::list<string> b;
        b.assign(std::begin(t), std::end(t));
        //        std::list<string> b{std::make_move_iterator(std::begin(t)),
        //                            std::make_move_iterator(std::end(t))};
        
        printf("string list %s\n", (a == b) ? "ok" : "not equal!");
        
        delete[] bytes;
    } catch (const char *msg) {
        delete[] bytes;
        printf("exception: %s\n", msg);
    }
}

bool compareAPtrVector(vector<A *> &a, vector<A *> &b) {
    int n = a.size();
    if (n != b.size()) {
        return false;
    }
    auto pA = a.data();
    auto pB = b.data();
    
    for (int i = 0; i < n; i++) {
        if (*pA[i] != *pB[i]) {
            return false;
        }
    }
    return true;
}

void testPackVector() {
    A a1(999);
    A a2(1000);
    vector<A *> a;
    a.push_back(&a1);
    a.push_back(&a2);
    char *bytes = nullptr;
    
    try {
        PackEncoder encoder = PackEncoder();
        encoder.putPackVector(0, reinterpret_cast<vector<Packable *> *>(&a));
        int count;
        bytes = encoder.getBytes(count);
        PackDecoder decoder = PackDecoder(bytes, 0, count);
        vector<A *> b;
        decoder.getPackPtrVector(0, b, A::decode);
        
        printf("Packable vector %s\n", compareAPtrVector(a, b) ? "ok" : "not equal!");
        
        for (auto &e : b) {
            delete e;
        }
        delete[] bytes;
    } catch (const char *msg) {
        delete[] bytes;
        printf("exception: %s\n", msg);
    }
}

void testStr2Str() {
    map<string, string> a;
    a.insert(std::pair<string, string>("test", ""));
    a.insert(std::pair<string, string>("test1", "1"));
    a.insert(std::pair<string, string>("test2", "2"));
    a.insert(std::pair<string, string>("test3", "3"));
    
    char *bytes = nullptr;
    try {
        PackEncoder encoder = PackEncoder();
        encoder.putStr2Str(0, a);
        int count;
        bytes = encoder.getBytes(count);
        
        PackDecoder decoder = PackDecoder(bytes, 0, count);
        map<string, string> b;
        decoder.getStr2Str(0, b);
        
        printf("string to string %s\n", (a == b) ? "ok" : "not equal!");
        
        delete[] bytes;
    } catch (const char *msg) {
        delete[] bytes;
        printf("exception: %s\n", msg);
    }
}


bool compareAPtrMap(map<string, A *> &a, map<string, A *> &b) {
    int n = a.size();
    if (n != b.size()) {
        return false;
    }
    
    for (auto &e : a) {
        if (*(e.second) != *(b[e.first])) {
            return false;
        }
    }
    
    return true;
}

void testStr2Pack() {
    A a1(100);
    A a2(300);
    
    map<string, A *> a;
    a.insert(std::pair<string, A *>("a1", &a1));
    a.insert(std::pair<string, A *>("a2", &a2));
    
    
    char *bytes = nullptr;
    try {
        PackEncoder encoder = PackEncoder();
        encoder.putStr2Pack(0, reinterpret_cast<map<string, Packable *> *>(&a));
        int count;
        bytes = encoder.getBytes(count);
        
        PackDecoder decoder = PackDecoder(bytes, 0, count);
        map<string, A *> b;
        decoder.getStr2PackPtr(0, b, A::decode);
        
        //a.erase("a1");
        
        printf("string to pack %s\n", compareAPtrMap(a, b) ? "ok" : "not equal!");
        
        for (auto &e : b) {
            delete e.second;
        }
        
        delete[] bytes;
    } catch (const char *msg) {
        delete[] bytes;
        printf("exception: %s\n", msg);
    }
}

void testStr2Int() {
    map<string, int> a;
    a.insert(std::pair<string, int>("test1", 1));
    a.insert(std::pair<string, int>("test2", 2));
    a.insert(std::pair<string, int>("test3", 3));
    
    char *bytes = nullptr;
    try {
        PackEncoder encoder = PackEncoder();
        encoder.putStr2Int(0, a);
        int count;
        bytes = encoder.getBytes(count);
        
        PackDecoder decoder = PackDecoder(bytes, 0, count);
        map<string, int> b;
        decoder.getStr2Int(0, b);
        
        printf("string to int map %s\n", (a == b) ? "ok" : "not equal!");
        delete[] bytes;
    } catch (const char *msg) {
        delete[] bytes;
        printf("exception: %s\n", msg);
    }
}

void testStr2Int64() {
    map<string, int64_t> a;
    a.insert(std::pair<string, int64_t>("test1", ((int64_t) 100) << 50));
    a.insert(std::pair<string, int64_t>("test2", 123));
    a.insert(std::pair<string, int64_t>("test3", 0xffffffffff));
    
    char *bytes = nullptr;
    try {
        PackEncoder encoder = PackEncoder();
        encoder.putStr2Int64(0, a);
        int count;
        bytes = encoder.getBytes(count);
        
        PackDecoder decoder = PackDecoder(bytes, 0, count);
        map<string, int64_t> b;
        decoder.getStr2Int64(0, b);
        
        //a.erase("test1");
        printf("string to int64 map %s\n", (a == b) ? "ok" : "not equal!");
        delete[] bytes;
    } catch (const char *msg) {
        delete[] bytes;
        printf("exception: %s\n", msg);
    }
}

void testStr2Float() {
    map<string, float> a;
    a.insert(std::pair<string, float>("test1", 1.5));
    a.insert(std::pair<string, float>("test2", 3.8));
    a.insert(std::pair<string, float>("test3", 6.9));
    
    char *bytes = nullptr;
    try {
        PackEncoder encoder = PackEncoder();
        encoder.putStr2Float(0, a);
        int count;
        bytes = encoder.getBytes(count);
        
        PackDecoder decoder = PackDecoder(bytes, 0, count);
        map<string, float> b;
        decoder.getStr2Float(0, b);
        
        printf("string to float map %s\n", (a == b) ? "ok" : "not equal!");
        delete[] bytes;
    } catch (const char *msg) {
        delete[] bytes;
        printf("exception: %s\n", msg);
    }
}

void testStr2Double() {
    map<string, double> a;
    a.insert(std::pair<string, double>("test1", 3.98));
    a.insert(std::pair<string, double>("test2", 1000.0));
    a.insert(std::pair<string, double>("test3", 300.0));
    
    char *bytes = nullptr;
    try {
        PackEncoder encoder = PackEncoder();
        encoder.putStr2Double(0, a);
        int count;
        bytes = encoder.getBytes(count);
        
        PackDecoder decoder = PackDecoder(bytes, 0, count);
        map<string, double> b;
        decoder.getStr2Double(0, b);
        
        printf("string to double map %s\n", (a == b) ? "ok" : "not equal!");
        delete[] bytes;
    } catch (const char *msg) {
        delete[] bytes;
        printf("exception: %s\n", msg);
    }
}

void testInt2Int() {
    map<int, int> a;
    a.insert(std::pair<int, int>(1, 100));
    a.insert(std::pair<int, int>(2, 200));
    a.insert(std::pair<int, int>(3, 300));
    
    char *bytes = nullptr;
    try {
        PackEncoder encoder = PackEncoder();
        encoder.putInt2Int(0, a);
        int count;
        bytes = encoder.getBytes(count);
        
        PackDecoder decoder = PackDecoder(bytes, 0, count);
        map<int, int> b;
        decoder.getInt2Int(0, b);
        
        printf("int to string map %s\n", (a == b) ? "ok" : "not equal!");
        delete[] bytes;
    } catch (const char *msg) {
        delete[] bytes;
        printf("exception: %s\n", msg);
    }
}

void testInt2Str() {
    map<int, string> a;
    a.insert(std::pair<int, string>(1, "test1"));
    a.insert(std::pair<int, string>(2, "test2"));
    a.insert(std::pair<int, string>(3, "test3"));
    
    char *bytes = nullptr;
    try {
        PackEncoder encoder = PackEncoder();
        encoder.putInt2Str(0, a);
        int count;
        bytes = encoder.getBytes(count);
        
        PackDecoder decoder = PackDecoder(bytes, 0, count);
        map<int, string> b;
        decoder.getInt2Str(0, b);
        
        printf("int to int map %s\n", (a == b) ? "ok" : "not equal!");
        delete[] bytes;
    } catch (const char *msg) {
        delete[] bytes;
        printf("exception: %s\n", msg);
    }
}

enum Season {
    SPRING, SUMMER, AUTUMN, WINTER
};

bool checkEnumArray(int *a, int n) {
    bool equal = false;
    char *bytes = nullptr;
    try {
        PackEncoder encoder = PackEncoder();
        encoder.putEnumArray(0, (int *) a, n);
        int count;
        bytes = encoder.getBytes(count);
        
        PackDecoder decoder = PackDecoder(bytes, 0, count);
        int bLen = 0;
        int *b = decoder.getEnumArray(0, bLen);
        equal = ((n == bLen) && (memcmp(a, b, n * 4) == 0));
        delete[] b;
        delete[] bytes;
    } catch (const char *msg) {
        delete[] bytes;
        printf("exception: %s\n", msg);
    }
    return equal;
}

void testEnumArray() {
    Season a[50];
    bool equal = false;
    for (int n = 10; n < 50; n += 5) {
        for (int i = 0; i < n; i++) {
            Season s;
            int x = rand() & 3;
            if (x == 0) s = SPRING;
            else if (x == 1) s = SUMMER;
            else if (x == 2) s = AUTUMN;
            else s = WINTER;
            a[i] = s;
        }
        equal = checkEnumArray((int *) a, n);
        if (!equal) break;
    }
    
    printf("Enum Array %s\n", equal ? "ok" : "not equal!");
}

void testEnumArray2() {
    int maskArray[4] = {0x1, 0x3, 0xf, 0xff};
    bool equal = false;
    for (int m = 0; m < 4; m++) {
        int mask = maskArray[m];
        for (int n = 31; n < 50; n += 3) {
            int *a = new int[n];
            for (int i = 0; i < n; i++) {
                a[i] = rand() & mask;
            }
            equal = checkEnumArray((int *) a, n);
            delete[] a;
            if (!equal) goto end;
        }
    }
end:
    printf("Enum Array 2 %s\n", equal ? "ok" : "not equal!");
}


void testIntArray(int n) {
    PackEncoder encoder = PackEncoder();
    int *a = new int[n];
    for (int i = 0; i < n; i++) {
        int x = rand();
        if (((x >> 16) & 0x3) == 1) {
            x = 0;
        } else{
            int shift = (x & 3) << 3;
            x = (uint32_t)x >> shift;
        }
        a[i] = x;
    }
    
    encoder.putCompactIntArray( 0, a, n);
    PackBuffer &buffer = encoder.getBuffer();
    
    PackDecoder decoder = PackDecoder(buffer.hb, 0, buffer.position);
    int bCount = 0;
    int *b = decoder.getCompactIntArray( 0, bCount);
    bool equal = n == bCount && memcmp(a, b, n * 4) == 0;
    delete[] a;
    delete[] b;
    if (!equal) {
        throw "testIntArray failed";
    }
}

void testInt64Array(int n) {
    PackEncoder encoder = PackEncoder();
    int64_t *a = new int64_t[n];
    for (int i = 0; i < n; i++) {
        int64_t x = rand();
        x = x * x;
        if (((x >> 16) & 0x3) == 1) {
            x = 0;
        } else{
            int shift = ((int) (x & 7)) << 3;
            x = (uint64_t)x >> shift;
        }
        a[i] = x;
    }
    
    encoder.putCompactInt64Array( 0, a, n);
    PackBuffer &buffer = encoder.getBuffer();
    
    PackDecoder decoder = PackDecoder(buffer.hb, 0, buffer.position);
    int bCount = 0;
    int64_t *b = decoder.getCompactInt64Array(0, bCount);
    bool equal = n == bCount && memcmp(a, b, n * 8) == 0;
    delete[] a;
    delete[] b;
    if (!equal) {
        throw "testInt64Array failed";
    }
}


bool testDoubleArray(double *a, int n) {
    PackEncoder encoder = PackEncoder();
    encoder.putCompactDoubleArray( 0, a, n);
    PackBuffer &buffer = encoder.getBuffer();
    
    PackDecoder decoder = PackDecoder(buffer.hb, 0, buffer.position);
    int bCount = 0;
    double *b = decoder.getCompactDoubleArray( 0, bCount);
    bool equal = n == bCount && memcmp(a, b, n * 8) == 0;
    delete[] b;
    return equal;
}

void testDoubleArray(int n) {
    double *a = new double[n];
    for (int i = 0; i < n; i++) {
        double x = rand() * 0.5f;
        a[i] = x;
    }
    bool equal = testDoubleArray(a, n);
    delete[] a;
    if (!equal) {
        throw "testDoubleArray failed";
    }
}

void testCompactNumberArray() {
    try {
        for (int n = 31; n < 200; n += 20) {
            testIntArray(n);
        }
        testIntArray(10001);
        testIntArray(30000);
        
        for (int n = 31; n < 200; n += 20) {
            testInt64Array(n);
        }
        testInt64Array(10001);
        testInt64Array(30000);
        
        double a[] = {-2, -1, 0, 0.5, 1, 2, 3, 4, 3.98,
            31, 32, 33, 63, 64, 1999,
            (1 << 21) - 1, 1 << 21, (1 << 21) + 1, ((uint64_t)1 << 55)-1.0};
        testDoubleArray(a, sizeof(a) / 8);
        
        for (int n = 31; n < 200; n += 50) {
            testDoubleArray(n);
        }
        testDoubleArray(10001);
        testDoubleArray(30000);
        printf("testNumberArray ok\n");
    } catch (const char *msg) {
        printf("msg: %s", msg);
    }
}

void testAll() {
    try {
        srand(1);
        
        testStrVector();
        testPackVector();
        
        testStr2Str();
        testStr2Pack();
        testStr2Int();
        testStr2Int64();
        testStr2Float();
        testStr2Double();
        testInt2Int();
        testInt2Str();
        
        testEnumArray();
        testEnumArray2();
        
        testCompactNumberArray();
        
        testCustomEncode();
    } catch (const char *msg) {
        printf("error msg: %s", msg);
    }
}


