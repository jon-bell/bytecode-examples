UNAME := $(shell uname)
CCFLAGS = -o target/taggingExample.o -I${JAVA_HOME}/include -c -fPIC -fpermissive
ifeq ($(UNAME), Linux)
	CCFLAGS += -I${JAVA_HOME}/include/linux 
	LINKFLAGS = -z defs -static-libgcc -shared -o target/libtaggingExample.so -lc 
endif
ifeq ($(UNAME), Darwin)
	CCFLAGS += -I${JAVA_HOME}/include/darwin
	LINKFLAGS += -dynamiclib -o target/libtaggingExample.so
endif

libtaggingExample.dylib:
	gcc ${CCFLAGS} src/main/c/tagger.cpp
	g++ ${LINKFLAGS} target/taggingExample.o
clean:
	rm -rf target/libtaggingExample.o target/libtaggingExample.dylib target/libtaggingExample.so
