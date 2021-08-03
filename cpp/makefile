PROG = pack
CC = g++
DIR = packable
EXAMPLE = example
OBJS = main.o PackEncoder.o PackDecoder.o CompactCoder.o PackVo.o Test.o
$(PROG) : $(OBJS)
	$(CC)  -o $(PROG) $(OBJS)
main.o:
	$(CC) -c  main.cpp
PackDecoder.o : $(DIR)/PackDecoder.h
	$(CC) -c  $(DIR)/PackDecoder.cpp 
PackEncoder.o : $(DIR)/PackEncoder.h
	$(CC) -c  $(DIR)/PackEncoder.cpp
CompactCoder.o: 
	$(CC) -c  $(DIR)/CompactCoder.cpp
PackVo.o:  $(EXAMPLE)/PackVo.h
	$(CC) -c  $(EXAMPLE)/PackVo.cpp
Test.o: $(EXAMPLE)/Test.h
	$(CC) -c $(EXAMPLE)/Test.cpp
	
clean:
	rm -f $(PROG) $(OBJS)